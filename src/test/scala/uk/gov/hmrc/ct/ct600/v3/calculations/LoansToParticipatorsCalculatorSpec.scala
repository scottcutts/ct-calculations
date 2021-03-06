/*
 * Copyright 2015 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.ct.ct600.v3.calculations

import org.joda.time.LocalDate
import org.scalatest.prop.TableDrivenPropertyChecks._
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.ct.computations.CP2
import uk.gov.hmrc.ct.ct600.v3.B485
import uk.gov.hmrc.ct.ct600a.v3._

class LoansToParticipatorsCalculatorSpec extends WordSpec with Matchers {

  def someDate(value:String):Option[LocalDate] = Some(new LocalDate(value))

  val lpq01Table = Table(
    ("lpq03", "lpq04", "expectedLpq01"),
    (Some(true), Some(true), true),
    (Some(true), Some(false), false),
    (Some(false), Some(true), false),
    (Some(false), Some(false), false),
    (None, None, false),
    (Some(false), None, false),
    (None, Some(false), false),
    (Some(true), None, false),
    (None, Some(true), false)
  )
  "LoansToParticipatorsCalculator" should {
    "correctly validate LPQ01 " in new LoansToParticipatorsCalculator {
      forAll(lpq01Table) {
        (lpq03: Option[Boolean], lpq04: Option[Boolean], expected: Boolean) => {
          calculateLPQ01(LPQ03(lpq03), LPQ04(lpq04)) shouldBe LPQ01(expected)
        }
      }
    }

    val loans2PTable = Table(
      ("expectedValue", "loans2p"),
      (0, LoansToParticipators(Nil)),
      (1, LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 1) :: Nil)),
      (6, LoansToParticipators(loans =
          Loan(id = "1", name = "Bilbo", amount = 1, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 99, date = new LocalDate("1939-09-02")))) ::
          Loan(id = "2", name = "Frodo", amount = 2, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 99, date = new LocalDate("1939-08-30")))) ::
          Loan(id = "3", name = "Gandalf", amount = 3, isRepaidWithin9Months = Some(false)) :: Nil))
    )
    "correctly calculate A15 (A2v2)" in new LoansToParticipatorsCalculator {
      forAll(loans2PTable) {
        (expectedValue: Int, loans2p: LoansToParticipators) => {
          calculateA15(loans2p) shouldBe A15(Some(expectedValue))
        }
      }
    }

    "correctly calculate A20 (A3v2)" in new LoansToParticipatorsCalculator {
      calculateA20(A15(Some(1))) shouldBe A20(Some(0.25))
      calculateA20(A15(Some(333))) shouldBe A20(Some(83.25))
    }


    val reliefDueNowOnLoanTable = Table(
      ("expectedValue", "isRepaid", "repaymentDate"),
      (true,            true,       Some(Repayment(id = "1", amount = 1, date = new LocalDate("2014-09-30")))),
      (false,           true,       Some(Repayment(id = "1", amount = 1, date = new LocalDate("2014-10-01"), someDate("2014-12-31")))), // illegal state - boolean says yes but repayment is outside 9 months
      (false,           true,       Some(Repayment(id = "1", amount = 1, date = new LocalDate("2013-12-31")))) // illegal state - boolean says yes but repayment is outside 9 months
    )
    "correctly calculate whether relief is due now for loans repaid within 9 months of end of AP" in new LoansToParticipatorsCalculator {
      forAll(reliefDueNowOnLoanTable) {
        (expectedValue: Boolean, isRepaid: Boolean, repayment: Option[Repayment]) => {
          val aLoan = Loan(id = "1", name = "Bilbo", amount = 10000, isRepaidWithin9Months = Some(isRepaid), repaymentWithin9Months = repayment)
          val acctPeriodEnd = new LocalDate("2013-12-31")
          aLoan.repaymentWithin9Months.get.isReliefEarlierThanDue(acctPeriodEnd) shouldBe expectedValue
        }
      }
    }

    val reliefDueNowOnWriteOffTable = Table(
      ("expectedValue", "dateWrittenOff"),
      (false,           "2013-12-31"), // during AP - too early
      (true,            "2014-01-01"), // ok - within 9 months
      (true,            "2014-09-30"), // ok - within 9 months
      (false,           "2014-10-01") // too late
    )
    "correctly calculate whether relief is due now for write offs made within 9 months of end of AP" in new LoansToParticipatorsCalculator {
      forAll(reliefDueNowOnWriteOffTable) {
        (expectedValue: Boolean, dateWrittenOff: String) => {

          val writeOff = WriteOff(id = "123", amount = 10, date = new LocalDate(dateWrittenOff), endDateOfAP = someDate("2050-12-31"))
          val acctPeriodEnd = new LocalDate("2013-12-31")
          writeOff.isReliefEarlierThanDue(acctPeriodEnd) shouldBe expectedValue
        }
      }
    }

    val a30Table = Table(
      ("expectedValue", "loans2p"),
      (None, LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 123, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 1, date = new LocalDate("2012-12-31")))) :: Nil)),  // illegal state - boolean says yes but repaid before AP end
      (Some(1), LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 123, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 1, date = new LocalDate("2013-01-01")))) :: Nil)),  // ok
      (Some(1), LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 123, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 1, date = new LocalDate("2013-09-30")))) :: Nil)), // ok
      (None, LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 123, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 1, date = new LocalDate("2013-10-01"), someDate("2013-12-31")))) :: Nil)), // repaid after 9 month period
      (None, LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 123, isRepaidWithin9Months = Some(false), repaymentWithin9Months = Some(Repayment(id = "1", amount = 1, date = new LocalDate("2013-01-01")))) :: Nil)), // illegal state - boolean says yes but repaid after 9 month period
      (Some(4), LoansToParticipators(loans =
          Loan(id = "1", name = "Bilbo", amount = 123, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 1, date = new LocalDate("2013-01-01")))) ::
          Loan(id = "1", name = "Frodo", amount = 456, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 2, date = new LocalDate("2012-12-31")))) ::
          Loan(id = "1", name = "Smaug", amount = 99999999, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 2, date = new LocalDate("2013-10-01"), someDate("2013-12-31")))) ::
          Loan(id = "1", name = "Gandalf", amount = 789, isRepaidWithin9Months = Some(true), repaymentWithin9Months = Some(Repayment(id = "1", amount = 3, date = new LocalDate("2013-09-30")))) :: Nil))
    )
    "correctly calculate A30 (A4v2) using loan repayments made between the end of the accounting period and 9months and 1 day later" in new LoansToParticipatorsCalculator {
      forAll(a30Table) {
        (expectedValue: Option[Int], loans2p: LoansToParticipators) => {
          val cp2 = CP2(new LocalDate("2012-12-31"))
          calculateA30(cp2, loans2p) shouldBe A30(expectedValue)
        }
      }
    }

    val a35Table = Table(
      ("expectedValue", "loans2p"),
      (None, LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(WriteOff("123", 1, new LocalDate("2012-12-31")))) :: Nil)), // too early
      (Some(1), LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 200, writeOffs = List(WriteOff("123", 1, new LocalDate("2013-01-01")))) :: Nil)),  // ok
      (Some(1), LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 250, writeOffs = List(WriteOff("123", 1, new LocalDate("2013-09-30")))) :: Nil)), // ok
      (None, LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 375, writeOffs = List(WriteOff("123", 1, new LocalDate("2013-10-01"), someDate("2013-12-31")))) :: Nil)),  // too late
      (Some(4), LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 50, writeOffs = List(
          WriteOff("123", 1, new LocalDate("2013-01-01")),
          WriteOff("456", 2, new LocalDate("2013-10-01"), someDate("2013-12-31")),
          WriteOff("789", 3, new LocalDate("2013-09-30")))) :: Nil)
        )
      )
    "correctly validate A35 (A5v2) using write offs made between the end of the accounting period and 9months and 1 day later" in new LoansToParticipatorsCalculator {
      forAll(a35Table) {
        (expectedValue: Option[Int], loans2p: LoansToParticipators) => {
          val cp2 = CP2(new LocalDate("2012-12-31"))
          calculateA35(cp2, loans2p) shouldBe A35(expectedValue)
        }
      }
    }

    "correctly calculate A40 (A6v2)" in new LoansToParticipatorsCalculator {
      calculateA40(A30(Some(4)), A35(Some(5))) shouldBe A40(Some(9))
      calculateA40(A30(None), A35(Some(5))) shouldBe A40(Some(5))
      calculateA40(A30(Some(4)), A35(None)) shouldBe A40(Some(4))
    }

    "correctly calculate A45 (A7v2)" in new LoansToParticipatorsCalculator {
      calculateA45(A40(Some(1))) shouldBe A45(Some(0.25))
      calculateA45(A40(Some(333))) shouldBe A45(Some(83.25))
    }

//    total of repayments made after (APend + 9months)
    val a55Table = Table(
      ("expectedValue", "loans2p", "filingDate"),
      (None, LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true), otherRepayments =
            List(Repayment(id = "1", amount = 1, date = new LocalDate("2013-08-31"), endDateOfAP = someDate("2013-12-31")))))), LPQ07(someDate("2014-10-01"))),  //illegal state - not >9months after AP end
      (Some(1), LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true),
            otherRepayments = List(Repayment(id = "1", amount = 1, date = new LocalDate("2013-10-01"), endDateOfAP = someDate("2013-12-31")))))), LPQ07(someDate("2014-10-01"))),  // ok
      (Some(1), LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true),
            otherRepayments = List(Repayment(id = "1", amount = 1, date = new LocalDate("2013-12-31"), endDateOfAP = someDate("2013-12-31")))))), LPQ07(someDate("2014-10-01"))),  // ok
      (None, LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true),
          otherRepayments = List(Repayment(id = "1", amount = 1, date = new LocalDate("2014-01-01"), endDateOfAP = someDate("2014-12-31")))))), LPQ07(someDate("2014-01-01"))),  // too late for this filing date
      (Some(6), LoansToParticipators(loans =
          Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true), otherRepayments = List(Repayment(id = "1", amount = 1, date = new LocalDate("2013-10-01"), endDateOfAP = someDate("2013-12-31"))) ) ::
          Loan(id = "2", name = "Frodo", amount = 456, hasOtherRepayments = Some(true), otherRepayments = List(Repayment(id = "1", amount = 3, date = new LocalDate("2013-09-30"), endDateOfAP = someDate("2013-12-31"))) ) ::
          Loan(id = "3", name = "Gandalf", amount = 789, hasOtherRepayments = Some(true), otherRepayments = List(Repayment(id = "1", amount = 5, date = new LocalDate("2014-01-31"), endDateOfAP = someDate("2014-12-31"))) ) :: Nil), LPQ07(someDate("2015-10-01")))
    )
    "correctly calculate A55 (A8v2) using loan repayments made more than 9 months after the end of the accounting period " in new LoansToParticipatorsCalculator {
      forAll(a55Table) {
        (expectedValue: Option[Int], loans2p: LoansToParticipators, filingDate: LPQ07) => {
          val apEndDate = CP2(new LocalDate("2012-12-31"))
          calculateA55(apEndDate, loans2p, filingDate) shouldBe A55(expectedValue)
        }
      }
    }

    val A55InverseTable = Table(
      ("expectedValue", "loans2p", "filingDate"),
      (None, LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true), otherRepayments =
          List(Repayment(id = "1", amount = 1, date = new LocalDate("2014-05-31"), endDateOfAP = someDate("2014-12-31")))))), LPQ07(someDate("2015-06-01"))), //repayment too early
      (None, LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true), otherRepayments =
          List(Repayment(id = "1", amount = 1, date = new LocalDate("2014-06-01"), endDateOfAP = someDate("2014-12-31")))))), LPQ07(someDate("2015-10-01"))), // relief due now
      (Some(1), LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true), otherRepayments =
          List(Repayment(id = "1", amount = 1, date = new LocalDate("2014-10-01"), endDateOfAP = someDate("2014-12-31")))))), LPQ07(someDate("2015-09-29"))), // filing date early - relief not yet due
      (Some(1), LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true), otherRepayments =
          List(Repayment(id = "1", amount = 1, date = new LocalDate("2014-11-01"), endDateOfAP = someDate("2014-12-31")))))), LPQ07(None)), // no filing date - meaning LPQ06 == true ie filied within 9 months
      (Some(2), LoansToParticipators(loans = List(
          Loan(id = "1", name = "Bilbo", amount = 123, hasOtherRepayments = Some(true), otherRepayments = List(Repayment(id = "1", amount = 1, date = new LocalDate("2014-05-31"), endDateOfAP = someDate("2014-12-31")))),
          Loan(id = "1", name = "Frodo", amount = 456, hasOtherRepayments = Some(true), otherRepayments = List(Repayment(id = "1", amount = 2, date = new LocalDate("2014-10-01"), endDateOfAP = someDate("2014-12-31")))),
          Loan(id = "1", name = "Gandalf", amount = 789, hasOtherRepayments = Some(true), otherRepayments = List(Repayment(id = "1", amount = 5, date = new LocalDate("2014-06-01"), endDateOfAP = someDate("2014-12-31"))))
      )), LPQ07(someDate("2015-09-30")))
    )
    "correctly calculate A55Inverse using loan repayments made more than 9 months after the end of the accounting period " in new LoansToParticipatorsCalculator {
      forAll(A55InverseTable) {
        (expectedValue: Option[Int], loans2p: LoansToParticipators, filingDate: LPQ07) => {
          val cp2 = CP2(new LocalDate("2013-12-31"))
          calculateA55Inverse(cp2, loans2p, filingDate) shouldBe A55Inverse(expectedValue)
        }
      }
    }


    val reliefLaterThanDueNowTable = Table(
      ("expectedValue", "isRepaid", "repaymentDate",  "endDateOfAccountingPeriodDuringWhichRepaymentWasMade",     "filingDate"),
      (false,           true,       "2014-09-30",     someDate("2014-12-31"),                                     someDate("2015-10-01")),  // repayment too early
      (true,            true,       "2014-10-01",     someDate("2014-12-31"),                                     someDate("2015-10-01")),  // ok
      (false,           true,       "2014-10-01",     someDate("2014-12-31"),                                     someDate("2015-09-30")),  // filing date too early
      (true,            true,       "2014-10-01",     someDate("2014-12-31"),                                     someDate("2015-10-01")), // ok
      (false,           true,       "2014-09-30",     None,                                                       someDate("2015-10-01"))  // illegal state - payment inside 9 months
    )
    "correctly calculate isRepaymentLaterReliefNowDue using loan repayments made more than 9 months after the end of the accounting period" in new LoansToParticipatorsCalculator {
      forAll(reliefLaterThanDueNowTable) {
        (expectedValue: Boolean, isRepaid:Boolean, repaymentDate:String, endDateOfAccountingPeriodDuringWhichRepaymentWasMade: Option[LocalDate], filingDate: Option[LocalDate]) => {
          val aLoan = Loan(id = "1", name = "Bilbo", amount = 10000, hasOtherRepayments = Some(isRepaid), otherRepayments =
            List(Repayment(id = "1", amount = 5000, date = new LocalDate(repaymentDate), endDateOfAP = endDateOfAccountingPeriodDuringWhichRepaymentWasMade)))
          val acctPeriodEnd = new LocalDate("2013-12-31")
          aLoan.otherRepayments.head.isLaterReliefNowDue(acctPeriodEnd, LPQ07(filingDate)) shouldBe expectedValue
        }
      }
    }

    val repaymentReliefLaterThanNotYetDueTable = Table(
      ("expectedValue", "isRepaid", "repaymentDate",        "endDateOfAccountingPeriodDuringWhichRepaymentWasMade",     "filingDate"),
      (false,           true,       "2014-09-30",     someDate("2014-12-31"),                                               someDate("2015-10-01")),  // repayment within 9 months
      (false,           true,       "2014-10-01",     someDate("2014-12-31"),                                               someDate("2015-10-01")),  // relief due now
      (true,            true,       "2014-10-01",     someDate("2014-12-31"),                                               someDate("2015-09-30")),  // filing date within 9 months - GOOD
      (false,           true,       "2014-10-01",     someDate("2014-12-31"),                                               someDate("2015-10-01")),  // filing date more that 9 months
      (false,           false,      "2014-10-01",     someDate("2014-12-31"),                                               someDate("2015-10-01")),  // not repaid
      (true,            true,       "2014-10-01",     someDate("2014-12-31"),                                               None),   // no filing date - meaning LPQ06 == true ie filied within 9 months
      (false,           true,       "2014-09-30",     None,                                                                 someDate("2015-10-01"))   // repayment within 9 months and no end of AP date
    )
    "correctly calculate isRepaymentLaterReliefNotYetDue using loan repayments made more than 9 months after the end of the accounting period" in new LoansToParticipatorsCalculator {
      forAll(repaymentReliefLaterThanNotYetDueTable) {
        (expectedValue: Boolean, isRepaid:Boolean, repaymentDate:String, endDateOfAccountingPeriodDuringWhichRepaymentWasMade: Option[LocalDate], filingDate: Option[LocalDate]) => {
          val aLoan = Loan(id = "1", name = "Bilbo", amount = 10000, hasOtherRepayments = Some(isRepaid), otherRepayments =
            List(Repayment(id = "1", amount = 5000, date = new LocalDate(repaymentDate), endDateOfAP = endDateOfAccountingPeriodDuringWhichRepaymentWasMade)))
          val acctPeriodEnd = new LocalDate("2013-12-31")
          aLoan.otherRepayments.head.isLaterReliefNotYetDue(acctPeriodEnd, LPQ07(filingDate)) shouldBe expectedValue
        }
      }
    }


    val writeOffReliefLaterThanNotYetDueTable = Table(
      ("expectedValue", "isRepaid",     "dateWrittenOff",  "endDateOfAccountingPeriodDuringWhichRepaymentWasMade",   "filingDate"),
      (false,           true,           "2014-09-30",       someDate("2014-12-31"),                                  someDate("2015-10-01")),  // repayment within 9 months
      (false,           true,           "2014-10-01",       someDate("2014-12-31"),                                  someDate("2015-10-01")),  // relief due now
      (true,            true,           "2014-10-01",       someDate("2014-12-31"),                                  someDate("2015-09-30")),  // filing date within 9 months - GOOD
      (false,           true,           "2014-10-01",       someDate("2014-12-31"),                                  someDate("2015-10-01")),  // filing date more that 9 months
      (false,           false,          "2014-10-01",       someDate("2014-12-31"),                                  someDate("2015-10-01"))   // not repaid
    )
    "correctly calculate isWriteOffLaterReliefNotYetDue using loan writeOffs made more than 9 months after the end of the accounting period" in new LoansToParticipatorsCalculator {
      forAll(writeOffReliefLaterThanNotYetDueTable) {
        (expectedValue: Boolean, isRepaid:Boolean, dateWrittenOff:String, endDateOfWriteOffAP: Option[LocalDate], filingDate: Option[LocalDate]) => {
          val writeOff = WriteOff(id = "123", amount = 10, date = new LocalDate(dateWrittenOff), endDateOfAP = endDateOfWriteOffAP)
          val acctPeriodEnd = new LocalDate("2013-12-31")
          writeOff.isLaterReliefNotYetDue(acctPeriodEnd, LPQ07(filingDate)) shouldBe expectedValue
        }
      }
    }


    "return false when writeOff date is within 9 months of the end date of AP and endDateOfWriteOffAP is None" in {
      val writeOff = WriteOff(id = "123", amount = 10, date = new LocalDate("2014-09-30"), endDateOfAP = None)
      val acctPeriodEnd = new LocalDate("2013-12-31")
      writeOff.isLaterReliefNowDue(acctPeriodEnd, LPQ07(someDate("2015-10-01"))) shouldBe false
    }


    val a60Table = Table(
      ("expectedValue", "loans2p", "filingDate"),
      (None, LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(WriteOff("123", 1, new LocalDate("2014-09-30"), someDate("2014-12-31")))))), someDate("2015-10-01")), // too early
      (None, LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(WriteOff("123", 1, new LocalDate("2014-09-30"), None))))), someDate("2015-10-01")),  // too early
      (Some(1), LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(WriteOff("123", 1, new LocalDate("2014-10-01"), someDate("2014-12-31")))))), someDate("2015-10-01")),  // ok
      (None, LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(WriteOff("123", 1, new LocalDate("2014-10-01"), someDate("2014-12-31")))))), someDate("2015-09-30")), // filing date too early
      (Some(6), LoansToParticipators(loans = List(Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(
          WriteOff("123", 1, new LocalDate("2014-10-01"), someDate("2014-12-31")),
          WriteOff("456", 2, new LocalDate("2014-09-30"), someDate("2014-12-31")),
          WriteOff("789", 5, new LocalDate("2014-12-31"), someDate("2014-12-31")))))), someDate("2015-10-01"))
    )
    "correctly calculate A60 (A9v2) using write offs made more than 9 months after the end of the accounting period" in new LoansToParticipatorsCalculator {
      forAll(a60Table) {
        (expectedValue: Option[Int], loans2p: LoansToParticipators, filingDate: Option[LocalDate]) => {
          val cp2 = CP2(new LocalDate("2013-12-31"))
          calculateA60(cp2, loans2p, LPQ07(filingDate)) shouldBe A60(expectedValue)
        }
      }
    }


    val a60InverseTable = Table(
      ("A9InverseExpectedValue", "loans2p", "filingDate"),
      (None, LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(WriteOff("123", 1, new LocalDate("2014-05-31"), someDate("2014-12-31")))) :: Nil), someDate("2015-06-01")),
      (None, LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(WriteOff("123", 1, new LocalDate("2014-06-01"), None))) :: Nil), someDate("2015-10-01")),
      (None, LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(WriteOff("123", 1, new LocalDate("2014-06-01"), someDate("2014-12-31")))) :: Nil), someDate("2014-10-01")),
      (Some(1), LoansToParticipators(loans = Loan(id = "1", name ="Bilbo", amount = 100, writeOffs = List(WriteOff("123", 1, new LocalDate("2014-10-01"), someDate("2014-12-31")))) :: Nil), someDate("2015-09-30")),
      (Some(2), LoansToParticipators(loans = Loan(id = "1", name = "Bilbo", amount = 100, writeOffs = List(
          WriteOff("123", 1, new LocalDate("2014-05-31"), someDate("2014-12-31")),
          WriteOff("456", 2, new LocalDate("2014-10-01"), someDate("2014-12-31")),
          WriteOff("789", 5, new LocalDate("2014-06-01"), someDate("2014-12-31")))) :: Nil), someDate("2015-09-30"))
    )
    "correctly calculate A60Inverse using write offs made more than 9 months after the end of the accounting period" in new LoansToParticipatorsCalculator {
      forAll(a60InverseTable) {
        (expectedValue: Option[Int], loans2p: LoansToParticipators, filingDate: Option[LocalDate]) => {
          val cp2 = CP2(new LocalDate("2013-12-31"))
          calculateA60Inverse(cp2, loans2p, LPQ07(filingDate)) shouldBe A60Inverse(expectedValue)
        }
      }
    }


    val writeOffRelief = Table(
      ("expectedValue",   "dateWrittenOff",   "endDateOfWriteOffAP",  "filingDate"),
      (false,             "1940-09-30",       "1940-12-31",           "1940-11-1"),
      (true,              "1940-10-01",       "1940-12-31",           "1941-10-1"),
      (false,             "1940-10-01",       "1940-12-31",           "1940-09-30")
    )

    "correctly calculate if relief is due on write offs after 9 months" in new LoansToParticipatorsCalculator {
      forAll(writeOffRelief) {
        (expectedValue: Boolean, dateWrittenOff: String, endDateOfWriteOffAP: String, filingDate: String) => {
          val cp2 = CP2(new LocalDate("1939-12-31"))
          val writeOff = WriteOff(id = "123", amount = 10, date = new LocalDate(dateWrittenOff), endDateOfAP = someDate(endDateOfWriteOffAP))
          writeOff.isLaterReliefNowDue(cp2.value, LPQ07(someDate(filingDate))) shouldBe expectedValue
        }
      }
    }


    "correctly calculate A65 (A10v2)" in new LoansToParticipatorsCalculator {
      calculateA65(A55(Some(4)), A60(Some(5))) shouldBe A65(Some(9))
      calculateA65(A55(None), A60(Some(5))) shouldBe A65(Some(5))
      calculateA65(A55(Some(4)), A60(None)) shouldBe A65(Some(4))
    }

    "correctly calculate A65Inverse" in new LoansToParticipatorsCalculator {
      calculateA65Inverse(A55Inverse(Some(4)), A60Inverse(Some(5))) shouldBe A65Inverse(Some(9))
      calculateA65Inverse(A55Inverse(None), A60Inverse(Some(5))) shouldBe A65Inverse(Some(5))
      calculateA65Inverse(A55Inverse(Some(4)), A60Inverse(None)) shouldBe A65Inverse(Some(4))
    }

    "correctly calculate A70 (A11v2)" in new LoansToParticipatorsCalculator {
      calculateA70(A65(Some(1))) shouldBe A70(Some(0.25))
      calculateA70(A65(Some(333))) shouldBe A70(Some(83.25))
    }

    "correctly calculate A70Inverse" in new LoansToParticipatorsCalculator {
      calculateA70Inverse(A65Inverse(Some(1))) shouldBe A70Inverse(Some(0.25))
      calculateA70Inverse(A65Inverse(Some(333))) shouldBe A70Inverse(Some(83.25))
    }

    "correctly calculate A75 (A12v2), total outstanding loans" in new LoansToParticipatorsCalculator {
      calculateA75(A15(None), LP04(None), A40(None), A65(None)) shouldBe A75(Some(0))
      calculateA75(A15(None), LP04(Some(4)), A40(None), A65(None)) shouldBe A75(Some(4))
      calculateA75(A15(None), LP04(None), A40(Some(6)), A65(None)) shouldBe A75(Some(-6))
      calculateA75(A15(None), LP04(None), A40(None), A65(Some(10))) shouldBe A75(Some(-10))
      calculateA75(A15(Some(40)), LP04(Some(60)), A40(Some(10)), A65(Some(20))) shouldBe A75(Some(70))
    }

    "correctly calculate A80 (A13v2)" in new LoansToParticipatorsCalculator {
      calculateA80(a20 = A20(Some(100)), a45 = A45(Some(7.99)), a70 = A70(Some(11))) shouldBe A80(Some(81.01))
      calculateA80(a20 = A20(Some(100.30)), a45 = A45(Some(7.99)), a70 = A70(Some(11))) shouldBe A80(Some(81.31))
      calculateA80(a20 = A20(Some(100)), a45 = A45(Some(7)), a70 = A70(Some(11))) shouldBe A80(Some(82))
      calculateA80(a20 = A20(Some(45.75)), a45 = A45(Some(7.25)), a70 = A70(Some(11))) shouldBe A80(Some(27.5))
      calculateA80(a20 = A20(Some(7.25)), a45 = A45(Some(7)), a70 = A70(Some(11))) shouldBe A80(Some(-10.75))
      calculateA80(a20 = A20(None), a45 = A45(None), a70 = A70(None)) shouldBe A80(None)
      calculateA80(a20 = A20(Some(100)), a45 = A45(None), a70 = A70(None)) shouldBe A80(Some(100))
    }

    "correctly calculate B485 (B80v2)" in new LoansToParticipatorsCalculator {
      calculateB485(A70(None)) shouldBe B485(false)
      calculateB485(A70(Some(0))) shouldBe B485(false)
      calculateB485(A70(Some(-1))) shouldBe B485(false)
      calculateB485(A70(Some(1))) shouldBe B485(true)
    }

  }


}

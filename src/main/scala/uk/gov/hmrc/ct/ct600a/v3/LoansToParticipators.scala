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

package uk.gov.hmrc.ct.ct600a.v3

import org.joda.time.LocalDate
import uk.gov.hmrc.ct.box._
import uk.gov.hmrc.ct.ct600.v3.retriever.CT600BoxRetriever
import uk.gov.hmrc.ct.ct600a.v3.formats.LoansFormatter


case class LoansToParticipators(loans: List[Loan] = List.empty) extends CtBoxIdentifier(name = "Loans to participators.") with CtValue[List[Loan]] with Input with ValidatableBox[CT600BoxRetriever] {

  def +(other: LoansToParticipators): LoansToParticipators = new LoansToParticipators(loans ++ other.loans)

  override def value = loans

  override def asBoxString = LoansFormatter.asBoxString(this)

  override def validate(boxRetriever: CT600BoxRetriever): Set[CtValidation] = {
    validateLoan(invalidLoanNameLength, "name", "error.loanNameLength") ++
    validateLoan(invalidLoanAmount, "amount", "error.loanAmount")
  }

  private def invalidLoanNameLength(loan: Loan): Boolean = loan.name.length < 2 || loan.name.length > 56

  private def invalidLoanAmount(loan: Loan): Boolean = loan.amount < 1 || loan.amount > 99999999

  def validateLoan(validate: Loan => Boolean, attributeName: String, errorMsg: String): Set[CtValidation] = {
    loans.find(validate) match {
      case Some(invalidLoan) => Set(CtValidation(Some(s"loan.${invalidLoan.id}.$attributeName"), s"loan.${invalidLoan.id}.$errorMsg", None))
      case _ => Set.empty
    }
  }
}

case class Loan ( id: String,
                  name: String,
                  amount: Int,
                  isRepaidWithin9Months: Option[Boolean] = None,
                  repaymentWithin9Months: Option[Repayment] = None,
                  hasOtherRepayments: Option[Boolean] = None,
                  otherRepayments: List[Repayment] = List.empty,
                  hasWriteOffs: Option[Boolean] = None,
                  writeOffs: List[WriteOff] = List.empty)

case class Repayment(id: String, amount: Int, date: LocalDate, endDateOfAP: Option[LocalDate] = None) extends LoansDateRules

case class WriteOff(id: String, amount: Int, date: LocalDate, endDateOfAP : Option[LocalDate] = None) extends LoansDateRules


trait LoansDateRules {

  val date: LocalDate
  val endDateOfAP: Option[LocalDate]

  def isReliefEarlierThanDue(acctPeriodEnd: LocalDate): Boolean = {
    val nineMonthsAndADayAfter: LocalDate = acctPeriodEnd.plusMonths(9).plusDays(1)
    date.isAfter(acctPeriodEnd) && date.isBefore(nineMonthsAndADayAfter)
  }

  def isLaterReliefNowDue(acctPeriodEnd: LocalDate, filingDate: LPQ07): Boolean = {
    !isReliefEarlierThanDue(acctPeriodEnd) && isFilingAfterLaterReliefDueDate(filingDate)
  }

  def isLaterReliefNotYetDue(acctPeriodEnd: LocalDate, filingDate: LPQ07): Boolean = {
    !isReliefEarlierThanDue(acctPeriodEnd) && isFilingBeforeReliefDueDate(filingDate)
  }

  private def isFilingAfterLaterReliefDueDate(filingDateBoxNumber: LPQ07): Boolean = {
    (filingDateBoxNumber.value, endDateOfAP) match {
      case (Some(filingDate), Some(endDate)) => {
        val reliefDueDate = endDate.plusMonths(9)
        filingDate.isAfter(reliefDueDate)
      }
      case _ => false
    }
  }

  private def isFilingBeforeReliefDueDate(filingDate: LPQ07) = filingDate.value match {
    case Some(date) => !isFilingAfterLaterReliefDueDate(filingDate)
    case None => true
  }

  // todo - this should morph into a loans2p validation rule when the validation is added
  private def requireEndDateOfApp(acctPeriodEnd: LocalDate) = {
    val message = s"As the repayment/writeOff date [$date] is more than 9 months after the accounting period end date [$acctPeriodEnd], the end date of the accounting period during which it was made must be provided"
    val requirement: Boolean = if (date.isAfter(acctPeriodEnd.plusMonths(9)) && endDateOfAP.isEmpty) false else true
    require(requirement, message)
  }
}

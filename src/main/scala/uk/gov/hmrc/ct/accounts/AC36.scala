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

package uk.gov.hmrc.ct.accounts

import uk.gov.hmrc.ct.accounts.calculations.ProfitOrLossCalculator
import uk.gov.hmrc.ct.accounts.retriever.AccountsBoxRetriever
import uk.gov.hmrc.ct.box.{CtOptionalInteger, CtBoxIdentifier, Calculated}

case class AC36(value: Option[Int]) extends CtBoxIdentifier(name = "Current Profit or loss for the financial year") with CtOptionalInteger

object AC36 extends Calculated[AC36, AccountsBoxRetriever] with ProfitOrLossCalculator {
  override def calculate(boxRetriever: AccountsBoxRetriever): AC36 = {
    calculateCurrentProfitOtLossAfterTax(ac32 = boxRetriever.retrieveAC32(),
                                         ac34 = boxRetriever.retrieveAC34())
  }
}

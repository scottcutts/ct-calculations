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

package uk.gov.hmrc.ct.ct600.v3

import uk.gov.hmrc.ct.box.{CtBoolean, Calculated, CtBoxIdentifier}
import uk.gov.hmrc.ct.ct600.v3.calculations.LoansToParticipatorsCalculator
import uk.gov.hmrc.ct.ct600.v3.retriever.CT600BoxRetriever

case class B485(value: Boolean) extends CtBoxIdentifier("Put an 'X' in box 485 if you completed box A70 in the supplementary pages CT600A") with CtBoolean

object B485 extends Calculated[B485, CT600BoxRetriever] with LoansToParticipatorsCalculator {

  override def calculate(fieldValueRetriever: CT600BoxRetriever): B485 = {
    calculateB485(fieldValueRetriever.retrieveA70())
  }

}
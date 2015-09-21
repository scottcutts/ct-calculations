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

package uk.gov.hmrc.ct.computations

import uk.gov.hmrc.ct.box.{Calculated, CtBoxIdentifier, CtInteger}
import uk.gov.hmrc.ct.computations.calculations.TotalAdditionsCalculator
import uk.gov.hmrc.ct.computations.retriever.ComputationsBoxRetriever

case class CP54(value: Int) extends CtBoxIdentifier(name = "Total Additions") with CtInteger

object CP54 extends Calculated[CP54, ComputationsBoxRetriever] with TotalAdditionsCalculator {

  override def calculate(fieldValueRetriever: ComputationsBoxRetriever): CP54 = {
    totalAdditionsCalculation(cp503 = fieldValueRetriever.retrieveCP503(),
                              cp46 = fieldValueRetriever.retrieveCP46(),
                              cp47 = fieldValueRetriever.retrieveCP47(),
                              cp48 = fieldValueRetriever.retrieveCP48(),
                              cp49 = fieldValueRetriever.retrieveCP49(),
                              cp51 = fieldValueRetriever.retrieveCP51(),
                              cp52 = fieldValueRetriever.retrieveCP52(),
                              cp53 = fieldValueRetriever.retrieveCP53())
  }
}
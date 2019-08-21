import chisel3._
import chisel3.util.{Cat, MuxCase}
import firrtl.{ExecutionOptionsManager, HasFirrtlOptions}

class PositAdd(totalBits: Int, es: Int) extends Module {
  require(totalBits > es)
  require(es >= 0)

  val io = IO(new Bundle {
    val num1 = Input(UInt(totalBits.W))
    val num2 = Input(UInt(totalBits.W))
    val out = Output(UInt(totalBits.W))
  })

  private val num1Fields = Module(new FieldsExtractor(totalBits, es))
  num1Fields.io.num := io.num1
  private val num1Sign = num1Fields.io.sign
  private val num1Exponent = num1Fields.io.exponent
  private val num1Fraction = num1Fields.io.fraction

  private val num2Fields = Module(new FieldsExtractor(totalBits, es))
  num2Fields.io.num := io.num2
  private val num2Sign = num2Fields.io.sign
  private val num2Exponent = num2Fields.io.exponent
  private val num2Fraction = num2Fields.io.fraction

  private val num1IsHigherExponent = num1Exponent > num2Exponent

  private val highestExponent = Mux(num1IsHigherExponent, num1Exponent, num2Exponent)
  private val highestExponentSign = Mux(num1IsHigherExponent, num1Sign, num2Sign)
  private val highestExponentFraction = Mux(num1IsHigherExponent, num1Fraction, num2Fraction)

  private val smallestExponent = Mux(num1IsHigherExponent, num2Exponent, num1Exponent)
  private val smallestExponentSign = Mux(num1IsHigherExponent, num2Sign, num1Sign)
  private val smallestExponentFraction = Mux(num1IsHigherExponent, num2Fraction, num1Fraction)

  private val exponentDifference = highestExponent - smallestExponent

  private val shiftingCombinations = Array.range(0, totalBits + 1).map(index => {
    (exponentDifference.asUInt() === index.U) -> smallestExponentFraction(totalBits, index)
  })

  private val smallestExponentFractionAfterAdjustment = MuxCase(0.U((totalBits + 2).W), shiftingCombinations)

  private val addedFraction = highestExponentFraction + smallestExponentFractionAfterAdjustment
  private val finalAddedFraction = Mux(addedFraction(totalBits + 1) === 1.U, addedFraction(totalBits, 1), Cat(addedFraction(totalBits - 1, 0)))
  private val finalAddedExponent = Mux(addedFraction(totalBits + 1) === 1.U, highestExponent + 1.S, highestExponent)

  private val isHighestExponentFractionHigher = highestExponentFraction >= smallestExponentFractionAfterAdjustment(totalBits,0)
  private val subtractedFraction = Mux(isHighestExponentFractionHigher,
    highestExponentFraction - smallestExponentFractionAfterAdjustment,
    smallestExponentFractionAfterAdjustment - highestExponentFraction
  )

  private val subtractionCombinationsForExponent = Array.range(0, totalBits + 1).map(index => {
    (subtractedFraction(totalBits,totalBits - index) === 1.U) -> index.U
  })

  private val subtractionCombinationsForFraction = Array.range(0, totalBits).map(index => {
    val int = if(index == 0) subtractedFraction(totalBits-1,0) else Cat(subtractedFraction(totalBits - index - 1, 0), 0.U(index.W))
    (subtractedFraction(totalBits,totalBits - index) === 1.U) -> int
  })

  private val int: SInt = Cat(0.U, MuxCase(totalBits.U, subtractionCombinationsForExponent)).asSInt()
  private val finalSubtractedExponent = Mux(int === totalBits.S, 0.S - (totalBits * math.pow(2,es).toInt).S, highestExponent - int)
  private val finalSubtractedFraction = MuxCase(0.U,subtractionCombinationsForFraction)
  private val finalSubtractedSign = Mux(isHighestExponentFractionHigher,highestExponentSign,smallestExponentSign)

  private val isSameSignAddition = !(highestExponentSign ^ smallestExponentSign)
  private val positGenerator = Module(new PositGenerator(totalBits, es))
  positGenerator.io.sign := Mux(isSameSignAddition,highestExponentSign,finalSubtractedSign)
  positGenerator.io.exponent := Mux(isSameSignAddition,finalAddedExponent,finalSubtractedExponent)
  positGenerator.io.fraction := Mux(isSameSignAddition,finalAddedFraction,finalSubtractedFraction)
  io.out := positGenerator.io.posit
}

object PositAdd extends App {
  val optionsManager = new ExecutionOptionsManager("chisel3") with HasChiselExecutionOptions with HasFirrtlOptions
  optionsManager.setTargetDirName("soc/chisel_output")
  Driver.execute(optionsManager, () => new PositAdd(8, 0))
}
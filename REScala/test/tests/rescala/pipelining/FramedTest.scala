package tests.rescala.pipelining

import org.scalatest.junit.AssertionsForJUnit
import org.scalatest.mock.MockitoSugar
import rescala.graph.Framed
import org.junit.Test
import rescala.turns.Turn
import rescala.graph.TurnFrame

class FramedTest extends AssertionsForJUnit with MockitoSugar {
  
  class TestFrame( turn : Turn, var num : Int) extends TurnFrame(turn){
    
    // Make turn accessible in test
    override def turn = super.turn
    
  }
  
  class TestFramed extends Framed {
    
    // Define TestFrame
    protected[this] override type Frame = TestFrame
    protected[this] def initialStableFrame: Frame = new TestFrame(null, 0)
    protected[this] def newFrameFrom(turn: Turn, other: Frame): Frame = new TestFrame(turn, other.num)
    
    // Make fields accessible in the test
    def getStableFrame() = stableFrame
    def getPipelineFrames() = pipelineFrames
  }
  
  val framed = new TestFramed()

  @Test
  def testInitialOnlyStable() = {
    assert(framed.getStableFrame() != null)
    assert(framed.getStableFrame().turn == null)
    assert(framed.getStableFrame().num == 0)
    assert(framed.getPipelineFrames().isEmpty)
  }
  
}
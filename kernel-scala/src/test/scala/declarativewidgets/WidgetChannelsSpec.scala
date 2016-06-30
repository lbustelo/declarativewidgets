package declarativewidgets

import declarativewidgets.util.MessageSupport
import org.apache.toree.comm.CommWriter
import org.apache.toree.kernel.protocol.v5._
import org.mockito.ArgumentCaptor
import org.mockito.Matchers._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, Entry, Matchers, FunSpec}
import org.mockito.Mockito._
import play.api.libs.json._

class WidgetChannelsSpec extends FunSpec with Matchers with MockitoSugar with BeforeAndAfterEach{

  class TestWidget(comm: CommWriter) extends WidgetChannels(comm)

  class TestWidgetNoChange(comm: CommWriter) extends WidgetChannels(comm) {
    override def handleChange(msgContent: MsgData) = Right(Unit)
  }

  override def beforeEach(): Unit = {
    WidgetChannels.theChannels = None
    WidgetChannels.cachedChannelData.clear()
    WidgetChannels.chanHandlers = Map()
  }

  describe("WidgetChannels") {

    describe("#watch") {
      it("integration: should execute the handler when the watched variable changes") {

        val test = new TestWidget(mock[CommWriter])

        val chan = "the_chan"
        val name = "x"

        var arg1: Int = -1

        var arg2: Int = -1

        val handler = (x: Option[Int], y: Int) => {arg1 = x.get; arg2 = y}

        WidgetChannels.channel(chan).watch(name, handler)

        val msg = Json.obj(
          Comm.KeyEvent -> Comm.EventChange,
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeOldVal -> JsNumber(0),
            Comm.ChangeNewVal -> JsNumber(1)
          )
        )

        test.handleChange(msg)

        arg1 should equal(0)
        arg2 should equal(1)
      }

      it("integration: should execute the handler when old_val is missing") {

        val test = new TestWidget(mock[CommWriter])

        val chan = "the_chan"
        val name = "x"

        var arg1: Option[Int] = null

        var arg2: Int = -1

        val handler = (x: Option[Int], y: Int) => {arg1 = x; arg2 = y}

        WidgetChannels.channel(chan).watch(name, handler)

        val msg = Json.obj(
          Comm.KeyEvent -> Comm.EventChange,
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeNewVal -> JsNumber(1)
          )
        )

        test.handleChange(msg)

        arg1 should equal(None)
        arg2 should equal(1)
      }
    }

    describe("#handleRequestState") {
      it("should send content of cachedChannelData"){
        WidgetChannels.channel().set("akey", "a value")
        WidgetChannels.channel("foo").set("anotherKey", "another value", 5)

        val comm = mock[CommWriter]
        val widget = spy(new WidgetChannels(comm))
        val msgSupport = spy(MessageSupport(mock[CommWriter]))

        widget.handleRequestState(mock[MsgData], msgSupport)

        val stateCaptor = ArgumentCaptor.forClass(classOf[Map[String,JsValue]])

        verify(msgSupport).sendState(stateCaptor.capture())

        val state = stateCaptor.getValue
        state.keys should contain theSameElementsAs(Seq("default:akey", "foo:anotherKey"))
        state("default:akey") should be (JsString("a value"))
        state("foo:anotherKey") should be (JsString("another value"))

        WidgetChannels.cachedChannelData shouldBe empty
      }
    }

    describe("#handleCustom") {
      it("should handle a change event using the message contents") {
        val test = spy(new TestWidgetNoChange(mock[CommWriter]))

        val msg = Json.obj(Comm.KeyEvent -> Comm.EventChange)
        test.handleCustom(msg, mock[MessageSupport])
        verify(test).handleChange(msg)
      }

      it("should not handle an invalid event") {
        val test = spy(new TestWidget(mock[CommWriter]))
        val msg = Json.obj(Comm.KeyEvent -> "asdf")
        test.handleCustom(msg, mock[MessageSupport])
        verify(test, times(0)).handleChange(any())
      }

      it("should send a status ok message if handling the change succeeded"){
        val test = spy(new TestWidget(mock[CommWriter]))
        val msgSupport = spy(MessageSupport(mock[CommWriter]))

        doReturn(Right(())).when(test).handleChange(any())
        val msg = Json.obj(Comm.KeyEvent -> Comm.EventChange)
        test.handleCustom(msg, msgSupport)
        verify(msgSupport).sendOk()
      }

      it("should send a status error message if handling the change fails"){
        val test = spy(new TestWidget(mock[CommWriter]))
        val msgSupport = spy(MessageSupport(mock[CommWriter]))

        doReturn(Left("uh oh")).when(test).handleChange(any())
        val msg = Json.obj(Comm.KeyEvent -> Comm.EventChange)
        test.handleCustom(msg, msgSupport)
        verify(msgSupport).sendError("uh oh")
      }
    }

    describe("#handleChange") {
      val chan = "c"
      val name = "x"

      it ("should invoke the watch handler for the given channel and name " +
         "with the given argument values") {
        val old = 1
        val noo = 2
        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeOldVal -> JsNumber(old),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        var executed = false
        val handler = (x: Option[Int], y: Int) => executed = true; ()
        WidgetChannels.watch(chan, name, handler)
        val wid = spy(new TestWidget(mock[CommWriter]))
        wid.handleChange(msg)

        executed should be(true)
      }

      it ("should auto convert numeric types to fit the type signature") {
        val old = 1
        val noo = 2.0
        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeOldVal -> JsNumber(old),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        var executed = false
        val handler = (x: Option[Double], y: Double) => executed = true; ()
        WidgetChannels.watch(chan, name, handler)
        val wid = spy(new TestWidget(mock[CommWriter]))
        wid.handleChange(msg)

        executed should be(true)
      }

      it ("should return Right when the channel is not registered") {
        val old = 1
        val noo = 2
        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString("not registered"),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeOldVal -> JsNumber(old),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        val wid = spy(new TestWidget(mock[CommWriter]))
        wid.handleChange(msg).isRight should be(true)
      }

      it ("should return Right when the name is not registered") {
        val old = 1
        val noo = 2
        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString("not registered"),
            Comm.ChangeOldVal -> JsNumber(old),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        var executed = false
        val handler = (x: Option[Int], y: Int) => executed = true; ()
        WidgetChannels.watch(chan, "DNE", handler)
        val wid = spy(new TestWidget(mock[CommWriter]))
        wid.handleChange(msg).isRight should be(true)

        executed should be(false)
      }

      it ("should return Right if oldVal is undefined") {
        val noo = 2
        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        var executed = false
        val handler = (x: Option[Int], y: Int) => executed = true; ()
        WidgetChannels.watch(chan, name, handler)
        val wid = spy(new TestWidget(mock[CommWriter]))
        wid.handleChange(msg).isRight should be(true)

      }

      it ("should return Left when invocation fails") {
        val old = 1
        val noo = 2
        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeOldVal -> JsNumber(old),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        val handler = (x: Option[Int], y: Int) => {val explosion = 1 / 0; ()}
        WidgetChannels.watch(chan, name, handler)
        val wid = spy(new TestWidget(mock[CommWriter]))
        wid.handleChange(msg).isLeft should be(true)
      }

      it ("should return Left if argument types don't match handler types") {
        val old = 1
        val noo = 2
        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeOldVal -> JsNumber(old),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        val handler = (x: Option[String], y: String) => ()
        WidgetChannels.watch(chan, name, handler)
        val wid = spy(new TestWidget(mock[CommWriter]))
        wid.handleChange(msg).isLeft should be(true)
      }

      it ("should return Left if the message format is invalid") {
        val msg = Json.obj()
        val handler = (x: Option[Int], y: Int) => ()
        WidgetChannels.watch(chan, name, handler)
        val wid = spy(new TestWidget(mock[CommWriter]))
        wid.handleChange(msg).isLeft should be(true)
      }
    }

    describe("#watch") {
      it ("should add a handler to the map for the given channel and name") {
        val chan = "chan2"
        val name = "n"
        val handler = (x: Option[String], y: String) => ()
        WidgetChannels.watch(chan, name, handler)
        WidgetChannels.chanHandlers(chan)(name) should be(handler)
      }

      it ("should overwrite an existing entry with the new handler") {
        val chan = "c1"
        val name = "n"
        val handler = (x: Option[String], y: String) => ()
        val handler2 = (x: Option[String], y: String) => ()
        WidgetChannels.watch(chan, name, handler)
        WidgetChannels.watch(chan, name, handler2)
        WidgetChannels.chanHandlers(chan)(name) should be(handler2)
      }
    }

    describe("#channel") {
      it ("should give a Channel object for the default channel using the " +
          "registered widget's comm when no channel argument is provided") {
        val comm = mock[CommWriter]
        val widget = mock[WidgetChannels]
        doReturn(comm).when(widget).comm
        WidgetChannels.register(widget)
        WidgetChannels.channel().chan should be(Default.Channel)

        WidgetChannels.channel() shouldBe a [Channel]

        WidgetChannels.channel().asInstanceOf[Channel].comm should be(comm)

      }
      it ("should give a Channel object for the given channel using the " +
        "registered widget's comm") {
        val comm = mock[CommWriter]
        val widget = mock[WidgetChannels]
        doReturn(comm).when(widget).comm
        WidgetChannels.register(widget)
        WidgetChannels.channel("foo").chan should be("foo")

        WidgetChannels.channel("foo") shouldBe a [Channel]

        WidgetChannels.channel("foo").asInstanceOf[Channel].comm should be(comm)
      }

      it ("should give a Channel object for the default channel that records to the cache map") {
        val comm = mock[CommWriter]

        val aChannel = WidgetChannels.channel()
        aChannel.chan should be(Default.Channel)

        aChannel.isInstanceOf[Channel] should be(false)

        aChannel.set("aKey", "a value")
        aChannel.set("anotherKey", "another value", 5)

        WidgetChannels.cachedChannelData.keys should contain only(Default.Channel)

        val channelData = WidgetChannels.cachedChannelData(Default.Channel)

        channelData should contain ("aKey" -> ("a value", Default.Limit))
        channelData should contain ("anotherKey" -> ("another value", 5))
      }

      it ("should give a Channel object for the given channel that records to the cache map") {
        val comm = mock[CommWriter]

        val aChannel = WidgetChannels.channel("foo")
        aChannel.chan should be("foo")

        aChannel.isInstanceOf[Channel] should be(false)

        aChannel.set("aKey", "a value")
        aChannel.set("anotherKey", "another value", 5)

        WidgetChannels.cachedChannelData.keys should contain only("foo")

        val channelData = WidgetChannels.cachedChannelData("foo")

        channelData should contain ("aKey" -> ("a value", Default.Limit))
        channelData should contain ("anotherKey" -> ("another value", 5))
      }
    }

    describe("init") {
      it ("should register the widget instance when created") {
        val comm = mock[CommWriter]
        val widget = new WidgetChannels(comm)
        WidgetChannels.theChannels.get should be (widget)
      }
    }

    describe("#parseMessage") {
      val chan = "c"
      val name = "x"

      it ("should give channel, name, oldVal, newVal for a valid message"){
        val old = 0
        val noo = 1

        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeOldVal -> JsNumber(old),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        val expected = Some((chan, name, JsNumber(old), JsNumber(noo)))

        val test = new TestWidget(mock[CommWriter])
        test.parseMessage(msg) should be (expected)
      }

      it ("should give return None given an incomplete message"){
        val noo = 1

        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        val expected = None

        val test = new TestWidget(mock[CommWriter])
        test.parseMessage(msg) should be (expected)
      }

      it ("should give return None if channel or name are not strings"){
        val old = 0
        val noo = 1

        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsNumber(0),
            Comm.ChangeName -> JsNumber(1),
            Comm.ChangeOldVal -> JsNumber(old),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        val expected = None

        val test = new TestWidget(mock[CommWriter])
        test.parseMessage(msg) should be (expected)
      }

      it ("should return Some if old_val is missing and fill it with JsNull"){
        val old = 0
        val noo = 1

        val msg = Json.obj(
          Comm.ChangeData -> Map(
            Comm.ChangeChannel -> JsString(chan),
            Comm.ChangeName -> JsString(name),
            Comm.ChangeNewVal -> JsNumber(noo)
          )
        )

        val expected = Some((chan, name, JsNull, JsNumber(noo)))

        val test = new TestWidget(mock[CommWriter])
        test.parseMessage(msg) should be (expected)
      }
    }

    describe("#getHandler") {
      it("should retrieve a registered handler"){
        val chan = "c"
        val name = "n"
        val handler = (x: Option[Int], y: Int) => ()
        Channel(mock[CommWriter], chan).watch(name, handler)

        val test = new TestWidget(mock[CommWriter])
        test.getHandler(chan, name) should be (Some(handler))
      }

      it("should return none if the requested handler does not exist") {
        val test = new TestWidget(mock[CommWriter])
        test.getHandler("", "") should be (None)
      }
    }
  }
}

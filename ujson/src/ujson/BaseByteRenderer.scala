package ujson
import scala.annotation.switch
import upickle.core.{ArrVisitor, ObjVisitor}

/**
	* A specialized JSON renderer that can render Bytes (Chars or Bytes) directly
	* to a [[java.io.Writer]] or [[java.io.OutputStream]]
	*
	* Note that we use an internal `ByteBuilder` to buffer the output internally
	* before sending it to [[out]] in batches. This lets us benefit from the high
	* performance and minimal overhead of `ByteBuilder` in the fast path of
	* pushing characters, and avoid the synchronization/polymorphism overhead of
	* [[out]] on the fast path. Most [[out]]s would also have performance
	* benefits from receiving data in batches, rather than elem by elem.
	*/
class BaseByteRenderer[T <: upickle.core.ByteOps.Output]
(out: T,
 indent: Int = -1,
 escapeUnicode: Boolean = false) extends JsVisitor[T, T]{
	private[this] val elemBuilder = new upickle.core.ByteBuilder
	private[this] val unicodeCharBuilder = new upickle.core.CharBuilder()
	def flushByteBuilder() = {
		elemBuilder.writeOutToIfLongerThan(out, if (depth == 0) 0 else 1000)
	}

	private[this] var depth: Int = 0


	private[this] var commaBuffered = false

	def flushBuffer() = {
		if (commaBuffered) {
			commaBuffered = false
			elemBuilder.append(',')
			renderIndent()
		}
	}
	def visitArray(length: Int, index: Int) = new ArrVisitor[T, T] {
		flushBuffer()
		elemBuilder.append('[')

		depth += 1
		renderIndent()
		def subVisitor = BaseByteRenderer.this
		def visitValue(v: T, index: Int): Unit = {
			flushBuffer()
			commaBuffered = true
		}
		def visitEnd(index: Int) = {
			commaBuffered = false
			depth -= 1
			renderIndent()
			elemBuilder.append(']')
			flushByteBuilder()
			out
		}
	}

	def visitObject(length: Int, index: Int) = new ObjVisitor[T, T] {
		flushBuffer()
		elemBuilder.append('{')
		depth += 1
		renderIndent()
		def subVisitor = BaseByteRenderer.this
		def visitKey(index: Int) = BaseByteRenderer.this
		def visitKeyValue(s: Any): Unit = {
			elemBuilder.append(':')
			if (indent != -1) elemBuilder.append(' ')
		}
		def visitValue(v: T, index: Int): Unit = {
			commaBuffered = true
		}
		def visitEnd(index: Int) = {
			commaBuffered = false
			depth -= 1
			renderIndent()
			elemBuilder.append('}')
			flushByteBuilder()
			out
		}
	}

	def visitNull(index: Int) = {
		flushBuffer()
		elemBuilder.ensureLength(4)
		elemBuilder.appendUnsafe('n')
		elemBuilder.appendUnsafe('u')
		elemBuilder.appendUnsafe('l')
		elemBuilder.appendUnsafe('l')
		flushByteBuilder()
		out
	}

	def visitFalse(index: Int) = {
		flushBuffer()
		elemBuilder.ensureLength(5)
		elemBuilder.appendUnsafe('f')
		elemBuilder.appendUnsafe('a')
		elemBuilder.appendUnsafe('l')
		elemBuilder.appendUnsafe('s')
		elemBuilder.appendUnsafe('e')
		flushByteBuilder()
		out
	}

	def visitTrue(index: Int) = {
		flushBuffer()
		elemBuilder.ensureLength(4)
		elemBuilder.appendUnsafe('t')
		elemBuilder.appendUnsafe('r')
		elemBuilder.appendUnsafe('u')
		elemBuilder.appendUnsafe('e')
		flushByteBuilder()
		out
	}

	def visitFloat64StringParts(s: CharSequence, decIndex: Int, expIndex: Int, index: Int) = {
		flushBuffer()
		elemBuilder.ensureLength(s.length())
		var i = 0
		val sLength = s.length
		while(i < sLength){
			elemBuilder.appendUnsafeC(s.charAt(i))
			i += 1
		}
		flushByteBuilder()
		out
	}

	override def visitFloat64(d: Double, index: Int) = {
		d match{
			case Double.PositiveInfinity => visitNonNullString("Infinity", -1)
			case Double.NegativeInfinity => visitNonNullString("-Infinity", -1)
			case d if java.lang.Double.isNaN(d) => visitNonNullString("NaN", -1)
			case d =>
				val i = d.toInt
				if (d == i) visitFloat64StringParts(i.toString, -1, -1, index)
				else super.visitFloat64(d, index)
				flushBuffer()
		}
		flushByteBuilder()
		out
	}


	def visitString(s: CharSequence, index: Int) = {

		if (s eq null) visitNull(index)
		else visitNonNullString(s, index)
	}

	def visitNonNullString(s: CharSequence, index: Int) = {
		flushBuffer()
		upickle.core.RenderUtils.escapeByte(unicodeCharBuilder, elemBuilder, s, escapeUnicode)
		flushByteBuilder()
		out
	}

	final def renderIndent() = {
		if (indent == -1) ()
		else {
			var i = indent * depth
			elemBuilder.ensureLength(i + 1)
			elemBuilder.appendUnsafe('\n')
			while(i > 0) {
				elemBuilder.appendUnsafe(' ')
				i -= 1
			}
		}
	}
}

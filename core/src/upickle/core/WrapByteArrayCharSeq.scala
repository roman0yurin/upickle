package upickle.core

/**
	* A [[CharSequence]] that wraps an array of elements without any copying.
	*
	* Note that the [[arr]] is mutable, and so the [[WrapByteArrayCharSeq]]
	* should not itself be stored: either use it immediately when given it
	* or call `.toString` if you want to store the data for later use.
	*/
class WrapByteArrayCharSeq(arr: Array[Byte], start: Int, length0: Int) extends CharSequence{
	//  assert(
	//    start < arr.length && start + length0 <= arr.length && start >= 0 && length0 >= 0,
	//    s"WrapByteArrayCharSeq(${arr.length}, $start, $length0)"
	//  )
	def length() = length0

	def charAt(index: Int) = {
		arr(index + start).toChar
	}

	def subSequence(newStart: Int, newEnd: Int) = {
		new WrapByteArrayCharSeq(arr, start + newStart, newEnd - newStart)
	}

	private[this] var toString0: String = null
	override def toString = {
		if (toString0 != null) toString0
		else {
			val res = new String(arr, start, length0)
			toString0 = res
			res
		}
	}
}

package upickle.core


/**
	* Models a growable [[buffer]] of Chars, which are Chars or Bytes. We maintain
	* an Array[Char] as a buffer, and read Chars into it using [[readDataIntoBuffer]]
	* and drop old Chars using [[dropBufferUntil]].
	*
	* In general, [[BufferingCharParser]] allows us to keep the fast path fast:
	*
	* - Reading elem-by-elem from the buffer is a bounds check and direct Array
	*   access, without any indirection or polymorphism.
	* - We load Chars in batches into the buffer, which allows us to take advantage
	*   of batching APIs like `InputStream.read`
	* - We amortize the overhead of the indirect/polymorphic [[readDataIntoBuffer]]
	*   call over the size of each batch
	*
	* Note that [[dropBufferUntil]] only advances a [[dropped]] index and does not
	* actually zero out the dropped Chars; instead, we wait until we need to call
	* [[growBuffer]], and use that as a chance to copy the remaining un-dropped Chars
	* to either the start of the current [[buffer]] or the start of a newly-allocated
	* bigger [[buffer]] (if necessary)
	*/
trait BufferingCharParser{

	private[this] var buffer: Array[Char] = null
	private[this] var bufferGrowCount = 0
	private[this] var bufferCopyCount = 0
	def getBufferGrowCount() = bufferGrowCount
	def getBufferCopyCount() = bufferCopyCount
	def getBufferLength() = if (buffer == null) -1 else buffer.length

	/**
		* The logical offset of the buffer(0) Char in the input being parsed
		*/
	private[this] var firstIdx = 0
	/**
		* The logical offset of the last Char in [[buffer]] that contains meaningfully
		* buffered data
		*/
	private[this] var lastIdx = 0
	/**
		* The logical offset of the last Char that we no longer care about in the
		* input being parsed; typically between [[firstIdx]] and [[lastIdx]]
		*/
	private[this] var dropped = 0
	/**
		* The earliest known value that is beyond the end of the input. Starts off unknown,
		* and we may record known values when a call to [[requestUntil]] returns `true` to
		* mark the end of input
		*/
	private[this] var knownEof = Int.MaxValue

	def getLastIdx = lastIdx

	def getCharSafe(i: Int): Char = {
		requestUntil(i)
		buffer(i - firstIdx)
	}
	def getCharUnsafe(i: Int): Char = {
		buffer(i - firstIdx)
	}

	def sliceString(i: Int, k: Int): String = {
		requestUntil(k - 1)
		new String(buffer, i - firstIdx, k - i)
	}

	def sliceArr(i: Int, n: Int): (Array[Char], Int, Int) = {
		requestUntil(i + n - 1)
		(buffer, i - firstIdx, n)
	}

	/**
		* Copies the non-dropped Chars in the current [[buffer]] to the start of either
		* the current [[buffer]], or a newly-allocated larger [[buffer]] if necessary.
		*/
	def growBuffer(until: Int) = {
		var newSize = buffer.length

		// Bump growGoalSiz by 50%. This helps ensure the utilization of the buffer
		// ranges from 33% to 66%, rather than from 50% to 100%. We want to avoid being
		// near 100% because we could end up doing large numbers of huge System.arraycopy
		// calls even when processing tiny amounts of data
		val growGoalSize = (until - dropped + 1) * 3 / 2
		while (newSize <= growGoalSize) newSize *= 2

		bufferCopyCount += 1
		val arr = if (newSize > buffer.length) {
			bufferGrowCount += 1
			new Array[Char](newSize)
		} else {
			buffer
		}

		System.arraycopy(buffer, dropped - firstIdx, arr, 0, lastIdx - dropped)
		firstIdx = dropped
		buffer = arr
	}

	/**
		* Used to ensure that elements up to [[until]] are available to read; returns
		* whether or not we have read off the end of the input.
		*
		* In the fast path, when [[until]] is less than the [[lastIdx]] we have buffered,
		* there is no work to do and we return false.
		*
		* In the slow path, when [[until]] is more than [[lastIdx]], we then run
		* [[growBuffer]] to grow the buffer if necessary, and then [[readDataIntoBuffer]]
		* to populate it. [[readDataIntoBuffer]] returns a `newDone` value to indicate
		* whether we have read off the end of the input or not.
		*
		* Note that for some subclasses, [[growBuffer]] may be a no-op when we already know
		* we have reached the end of input.
		*/
	protected def requestUntil(until: Int): Boolean = {
		if (until < lastIdx) false
		else if (until >= knownEof) true
		else requestUntil0(until)
	}

	private def requestUntil0(until: Int) = {
		val untilBufferOffset = until - firstIdx
		if (buffer != null && untilBufferOffset >= buffer.length) growBuffer(until)

		val bufferOffset = lastIdx - firstIdx
		val (newBuffer, newDone, n) = readDataIntoBuffer(buffer, bufferOffset)
		buffer = newBuffer

		lastIdx = lastIdx + n
		if (newDone) knownEof = until
		newDone
	}

	def readDataIntoBuffer(buffer: Array[Char], bufferOffset: Int): (Array[Char], Boolean, Int)

	def dropBufferUntil(i: Int): Unit = {
		dropped = i
	}
	def unsafeCharSeqForRange(start: Int, length: Int) = {
		new WrapCharArrayCharSeq(buffer, start - firstIdx, length)
	}

	def appendCharsToBuilder(elems: CharBuilder, elemsStart: Int, elemsLength: Int) = {
		elems.appendAll(buffer, elemsStart - firstIdx, elemsLength)
	}
}

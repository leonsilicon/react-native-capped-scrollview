package com.cappedscrollview

import android.os.Handler
import android.os.Looper
import android.widget.OverScroller
import android.widget.ScrollView
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.common.UIManagerType
import com.facebook.react.views.scroll.ReactScrollView
import java.lang.reflect.Field
import kotlin.math.abs

@ReactModule(name = CappedScrollViewModule.NAME)
class CappedScrollViewModule(reactContext: ReactApplicationContext) :
  NativeCappedScrollViewSpec(reactContext) {

  private val mainHandler = Handler(Looper.getMainLooper())

  override fun getName(): String = NAME

  override fun setMaxVelocity(reactTag: Double, maxVelocity: Double) {
    val tag = reactTag.toInt()
    attemptInstall(tag, maxVelocity, attemptsRemaining = MAX_RESOLVE_ATTEMPTS)
  }

  private fun attemptInstall(tag: Int, value: Double, attemptsRemaining: Int) {
    mainHandler.post {
      val view = resolveView(reactApplicationContext, tag)
      if (view !is ReactScrollView) {
        if (attemptsRemaining > 0) {
          mainHandler.postDelayed({
            attemptInstall(tag, value, attemptsRemaining - 1)
          }, RETRY_DELAY_MS)
        }
        return@post
      }
      if (value < 0) {
        uninstallCap(view)
      } else {
        installCap(view, value)
      }
    }
  }

  private fun uninstallCap(view: ReactScrollView) {
    val baseField = scrollViewScrollerField() ?: return
    val current = try {
      baseField.get(view) as? OverScroller
    } catch (_: Throwable) {
      null
    }
    val original = (current as? CappedOverScroller)?.original ?: return
    try {
      baseField.set(view, original)
    } catch (_: Throwable) {
      return
    }
    try {
      reactScrollViewScrollerField()?.set(view, original)
    } catch (_: Throwable) {
      // Best-effort.
    }
  }

  private fun resolveView(context: ReactContext, tag: Int): android.view.View? {
    val byTag = try {
      UIManagerHelper.getUIManagerForReactTag(context, tag)?.resolveView(tag)
    } catch (_: Throwable) {
      null
    }
    if (byTag != null) return byTag
    return try {
      UIManagerHelper.getUIManager(context, UIManagerType.FABRIC)?.resolveView(tag)
    } catch (_: Throwable) {
      null
    }
  }

  private fun installCap(view: ReactScrollView, fraction: Double) {
    val baseField = scrollViewScrollerField() ?: return
    val rnField = reactScrollViewScrollerField()
    val current = try {
      baseField.get(view) as? OverScroller
    } catch (_: Throwable) {
      null
    }
    if (current is CappedOverScroller) {
      current.fraction = fraction
      return
    }
    // The cap reference is 8000 dp/s — the same numeric value used on iOS,
    // expressed in dp/pt so the two platforms produce the same scroll feel
    // at the same `maxVelocity` value. Convert to physical pixels for the
    // OverScroller (which accepts px/s).
    val density = view.resources.displayMetrics.density
    val referenceMaxPxPerSec = REFERENCE_MAX_DP_PER_SEC * density
    val replacement = CappedOverScroller(
      view.context,
      fraction,
      referenceMaxPxPerSec.toDouble(),
      current,
    )
    try {
      baseField.set(view, replacement)
    } catch (_: Throwable) {
      return
    }
    try {
      rnField?.set(view, replacement)
    } catch (_: Throwable) {
      // Best-effort; RN's cached reference will fall back to base ScrollView behavior.
    }
  }

  private fun scrollViewScrollerField(): Field? = scrollerFieldCache ?: run {
    try {
      val field = ScrollView::class.java.getDeclaredField("mScroller")
      field.isAccessible = true
      scrollerFieldCache = field
      field
    } catch (_: Throwable) {
      null
    }
  }

  private fun reactScrollViewScrollerField(): Field? = rnScrollerFieldCache ?: run {
    try {
      val field = ReactScrollView::class.java.getDeclaredField("mScroller")
      field.isAccessible = true
      rnScrollerFieldCache = field
      field
    } catch (_: NoSuchFieldException) {
      null
    }
  }

  companion object {
    const val NAME = "CappedScrollView"
    private const val RETRY_DELAY_MS = 33L
    private const val MAX_RESOLVE_ATTEMPTS = 30
    // Public 0..1 cap scale is anchored to 8000 dp/s (matches iOS's 8000 pt/s).
    private const val REFERENCE_MAX_DP_PER_SEC = 8000f
    @Volatile private var scrollerFieldCache: Field? = null
    @Volatile private var rnScrollerFieldCache: Field? = null
  }
}

/**
 * OverScroller subclass that caps peak fling velocity at
 * `referenceMax * fraction` (where `referenceMax` is 8000 dp/s converted to
 * physical pixels, matching iOS's 8000 pt/s reference). The base RN/Android
 * deceleration physics are otherwise left alone, so the resulting fling
 * decelerates naturally from the clamped peak.
 */
private class CappedOverScroller(
  context: android.content.Context,
  @Volatile var fraction: Double,
  private val referenceMaxPxPerSec: Double,
  val original: OverScroller?,
) : OverScroller(context) {

  override fun fling(
    startX: Int,
    startY: Int,
    velocityX: Int,
    velocityY: Int,
    minX: Int,
    maxX: Int,
    minY: Int,
    maxY: Int,
    overX: Int,
    overY: Int,
  ) {
    val (cx, cy) = applyCap(velocityX, velocityY)
    super.fling(startX, startY, cx, cy, minX, maxX, minY, maxY, overX, overY)
  }

  override fun fling(
    startX: Int,
    startY: Int,
    velocityX: Int,
    velocityY: Int,
    minX: Int,
    maxX: Int,
    minY: Int,
    maxY: Int,
  ) {
    val (cx, cy) = applyCap(velocityX, velocityY)
    super.fling(startX, startY, cx, cy, minX, maxX, minY, maxY)
  }

  private fun applyCap(velocityX: Int, velocityY: Int): Pair<Int, Int> {
    val f = fraction.coerceIn(0.0, 1.0)
    if (f >= 1.0) return velocityX to velocityY
    if (f <= 0.0) return 0 to 0
    val cap = referenceMaxPxPerSec * f
    val peak = maxOf(abs(velocityX), abs(velocityY)).toDouble()
    if (peak <= cap) return velocityX to velocityY
    val scale = cap / peak
    return (velocityX * scale).toInt() to (velocityY * scale).toInt()
  }
}

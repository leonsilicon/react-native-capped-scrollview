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
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import kotlin.math.abs

@ReactModule(name = CappedScrollViewModule.NAME)
class CappedScrollViewModule(reactContext: ReactApplicationContext) :
  NativeCappedScrollViewSpec(reactContext) {

  private val mainHandler = Handler(Looper.getMainLooper())

  override fun getName(): String = NAME

  override fun setMaxVelocity(reactTag: Double, maxVelocity: Double) {
    val tag = reactTag.toInt()
    attemptInstall(tag, maxVelocity, attemptsRemaining = 30)
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
    val decelerationRate = try {
      view.reactScrollViewScrollState.decelerationRate
    } catch (_: Throwable) {
      DEFAULT_DECELERATION_RATE
    }
    val initialFriction = (1f - decelerationRate).coerceAtLeast(MIN_FRICTION)
    val platformMax = android.view.ViewConfiguration.get(view.context)
      .scaledMaximumFlingVelocity
      .toDouble()
    val replacement = CappedOverScroller(
      view.context,
      fraction,
      initialFriction,
      platformMax,
      current,
      WeakReference(view),
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
    private const val DEFAULT_DECELERATION_RATE = 0.985f
    private const val MIN_FRICTION = 0.0001f
    @Volatile private var scrollerFieldCache: Field? = null
    @Volatile private var rnScrollerFieldCache: Field? = null
  }
}

/**
 * OverScroller that caps peak fling velocity. The cap is a normalized
 * fraction in [0, 1] of the platform's max fling velocity. When the
 * requested fling velocity exceeds the cap, the velocity is clamped to
 * `platformMax * fraction` and friction is lowered proportionally so the
 * fling still covers a reasonable distance instead of stopping abruptly.
 */
private class CappedOverScroller(
  context: android.content.Context,
  @Volatile var fraction: Double,
  initialFriction: Float,
  private val platformMax: Double,
  val original: OverScroller?,
  private val viewRef: WeakReference<ReactScrollView>,
) : OverScroller(context) {

  @Volatile private var baseFriction: Float = initialFriction

  init {
    setFriction(initialFriction)
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

  private fun refreshBaseFriction() {
    val view = viewRef.get() ?: return
    val rate = try {
      view.reactScrollViewScrollState.decelerationRate
    } catch (_: Throwable) {
      return
    }
    baseFriction = (1f - rate).coerceAtLeast(MIN_FRICTION)
  }

  private fun applyCap(velocityX: Int, velocityY: Int): Pair<Int, Int> {
    refreshBaseFriction()
    val f = fraction.coerceIn(0.0, 1.0)
    val base = baseFriction
    if (f >= 1.0) {
      // At 1.0 the cap equals the platform's own clamp — let the natural
      // physics run, no friction adjustment.
      setFriction(base)
      return velocityX to velocityY
    }
    if (f <= 0.0) {
      setFriction(base)
      return 0 to 0
    }
    val cap = platformMax * f
    val peak = maxOf(abs(velocityX), abs(velocityY)).toDouble()
    if (peak <= cap) {
      setFriction(base)
      return velocityX to velocityY
    }
    // Speed-limit model: lower friction by sqrt(cap/peak) so the fling
    // distance feels natural rather than truncated by the lower start
    // velocity alone.
    val ratio = kotlin.math.sqrt(cap / peak).toFloat()
    setFriction((base * ratio).coerceAtLeast(MIN_FRICTION))
    val capInt = if (cap > Int.MAX_VALUE) Int.MAX_VALUE else cap.toInt()
    val cx = velocityX.coerceIn(-capInt, capInt)
    val cy = velocityY.coerceIn(-capInt, capInt)
    return cx to cy
  }

  companion object {
    private const val MIN_FRICTION = 0.00005f
  }
}

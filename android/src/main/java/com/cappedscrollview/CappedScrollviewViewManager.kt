package com.cappedscrollview

import android.graphics.Color
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.CappedScrollviewViewManagerInterface
import com.facebook.react.viewmanagers.CappedScrollviewViewManagerDelegate

@ReactModule(name = CappedScrollviewViewManager.NAME)
class CappedScrollviewViewManager : SimpleViewManager<CappedScrollviewView>(),
  CappedScrollviewViewManagerInterface<CappedScrollviewView> {
  private val mDelegate: ViewManagerDelegate<CappedScrollviewView>

  init {
    mDelegate = CappedScrollviewViewManagerDelegate(this)
  }

  override fun getDelegate(): ViewManagerDelegate<CappedScrollviewView>? {
    return mDelegate
  }

  override fun getName(): String {
    return NAME
  }

  public override fun createViewInstance(context: ThemedReactContext): CappedScrollviewView {
    return CappedScrollviewView(context)
  }

  @ReactProp(name = "color")
  override fun setColor(view: CappedScrollviewView?, color: Int?) {
    view?.setBackgroundColor(color ?: Color.TRANSPARENT)
  }

  companion object {
    const val NAME = "CappedScrollviewView"
  }
}

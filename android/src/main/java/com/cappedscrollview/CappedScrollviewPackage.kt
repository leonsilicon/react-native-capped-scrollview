package com.cappedscrollview

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class CappedScrollViewPackage : BaseReactPackage() {
  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
    if (name == CappedScrollViewModule.NAME) CappedScrollViewModule(reactContext) else null

  override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
    mapOf(
      CappedScrollViewModule.NAME to ReactModuleInfo(
        CappedScrollViewModule.NAME,
        CappedScrollViewModule::class.java.name,
        false,
        false,
        false,
        true,
      )
    )
  }
}

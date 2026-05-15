#import "CappedScrollView.h"

#import <React/RCTBridge.h>
#import <React/RCTBridgeModule.h>
#import <React/RCTScrollViewComponentView.h>
#import <React/RCTUtils.h>

@interface CappedScrollViewVelocityCapper : NSObject <UIScrollViewDelegate>
@property (nonatomic, assign) CGFloat maxVelocity;
@end

static RCTScrollViewComponentView *_Nullable CappedScrollViewSearch(UIView *root, NSInteger tag)
{
  if (root.tag == tag && [root isKindOfClass:[RCTScrollViewComponentView class]]) {
    return (RCTScrollViewComponentView *)root;
  }
  for (UIView *child in root.subviews) {
    RCTScrollViewComponentView *match = CappedScrollViewSearch(child, tag);
    if (match) {
      return match;
    }
  }
  return nil;
}

static RCTScrollViewComponentView *_Nullable CappedScrollViewFindScrollView(NSInteger tag)
{
  for (UIScene *scene in UIApplication.sharedApplication.connectedScenes) {
    if (![scene isKindOfClass:[UIWindowScene class]]) continue;
    for (UIWindow *window in ((UIWindowScene *)scene).windows) {
      RCTScrollViewComponentView *match = CappedScrollViewSearch(window, tag);
      if (match) {
        return match;
      }
    }
  }
  return nil;
}

// Cross-platform reference for the public 0..1 cap scale: 8000 pt/s on iOS,
// 8000 dp/s on Android. At maxVelocity=1 the cap exactly matches this value;
// at 0.5 it is 4000 pt/s, etc.
static const CGFloat kCappedScrollViewReferenceMaxPtsPerSec = 8000.0;

@implementation CappedScrollViewVelocityCapper

- (void)scrollViewWillEndDragging:(UIScrollView *)scrollView
                     withVelocity:(CGPoint)velocity
              targetContentOffset:(inout CGPoint *)targetContentOffset
{
  CGFloat fraction = MAX(0.0, MIN(1.0, self.maxVelocity));
  if (fraction >= 1.0) {
    return;
  }
  if (fraction <= 0.0) {
    *targetContentOffset = scrollView.contentOffset;
    return;
  }

  // UIScrollView reports velocity in points-per-millisecond.
  CGFloat capPerMs = (kCappedScrollViewReferenceMaxPtsPerSec * fraction) / 1000.0;
  CGFloat peak = MAX(fabs(velocity.x), fabs(velocity.y));
  if (peak <= capPerMs) {
    // Natural fling already within the cap — leave it untouched.
    return;
  }

  // Speed-limit model: scale the predicted target offset toward current by
  // (cap / peak). Linear scaling here means the fling distance is exactly
  // proportional to how much we've clamped peak velocity, matching the
  // Android behaviour (which clamps `velocityY` to `cap` directly).
  CGFloat scale = capPerMs / peak;
  CGPoint current = scrollView.contentOffset;
  CGPoint target = *targetContentOffset;
  target.x = current.x + (target.x - current.x) * scale;
  target.y = current.y + (target.y - current.y) * scale;
  *targetContentOffset = target;
}

@end

@implementation CappedScrollView {
  NSMapTable<NSNumber *, CappedScrollViewVelocityCapper *> *_cappersByTag;
}

RCT_EXPORT_MODULE()

@synthesize bridge = _bridge;
@synthesize viewRegistry_DEPRECATED = _viewRegistry_DEPRECATED;

- (instancetype)init
{
  if (self = [super init]) {
    _cappersByTag = [NSMapTable strongToStrongObjectsMapTable];
  }
  return self;
}

- (void)setMaxVelocity:(double)reactTag maxVelocity:(double)maxVelocity
{
  NSInteger tag = (NSInteger)reactTag;
  CGFloat cap = (CGFloat)maxVelocity;
  __weak __typeof(self) weakSelf = self;
  [self attemptInstallForTag:tag cap:cap attemptsRemaining:30 weakSelf:weakSelf];
}

- (void)attemptInstallForTag:(NSInteger)tag
                         cap:(CGFloat)cap
           attemptsRemaining:(NSInteger)attemptsRemaining
                    weakSelf:(__weak CappedScrollView *)weakSelf
{
  RCTExecuteOnMainQueue(^{
    __typeof(self) strongSelf = weakSelf;
    if (!strongSelf) {
      return;
    }

    RCTScrollViewComponentView *scrollComponent = CappedScrollViewFindScrollView(tag);
    if (!scrollComponent) {
      if (attemptsRemaining > 0) {
        // Fabric mounting can lag the JS-side ref attachment; retry up to ~1s.
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(33 * NSEC_PER_MSEC)),
                       dispatch_get_main_queue(), ^{
          [strongSelf attemptInstallForTag:tag
                                       cap:cap
                         attemptsRemaining:attemptsRemaining - 1
                                  weakSelf:weakSelf];
        });
      }
      return;
    }

    NSNumber *key = @(tag);
    CappedScrollViewVelocityCapper *capper = [strongSelf->_cappersByTag objectForKey:key];

    if (cap < 0) {
      // Sentinel: caller wants no capper at all.
      if (capper) {
        [scrollComponent.scrollViewDelegateSplitter removeDelegate:capper];
        [strongSelf->_cappersByTag removeObjectForKey:key];
      }
      return;
    }

    if (!capper) {
      capper = [CappedScrollViewVelocityCapper new];
      [strongSelf->_cappersByTag setObject:capper forKey:key];
      [scrollComponent.scrollViewDelegateSplitter addDelegate:capper];
    }
    capper.maxVelocity = cap;
  });
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeCappedScrollViewSpecJSI>(params);
}

@end

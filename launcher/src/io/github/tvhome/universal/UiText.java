package io.github.tvhome.universal;
public final class UiText extends android.app.Application {
    private static UiText app;
    @Override public void onCreate(){super.onCreate();app=this;NavigationSound.init(this);WakeSoundController.init(this);WatchTimerController.init(this);
        registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks(){
            public void onActivityResumed(android.app.Activity activity){WakeSoundController.foreground();NavigationSound.attach(activity.getWindow().getDecorView());NavigationSound.attachWindow(activity.getWindow());}
            public void onActivityCreated(android.app.Activity activity,android.os.Bundle state){}
            public void onActivityStarted(android.app.Activity activity){}
            public void onActivityPaused(android.app.Activity activity){}
            public void onActivityStopped(android.app.Activity activity){}
            public void onActivitySaveInstanceState(android.app.Activity activity,android.os.Bundle state){}
            public void onActivityDestroyed(android.app.Activity activity){}
        });}
    static String get(int id){return app.getResources().getString(id);}
    static java.util.Locale locale(){
        android.content.res.Configuration config=app.getResources().getConfiguration();
        return config.getLocales().isEmpty()?java.util.Locale.getDefault():config.getLocales().get(0);
    }
}

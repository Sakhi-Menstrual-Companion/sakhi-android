plugins {
    id("sakhi.android.library")
}

android {
    namespace = "team.sakhi.android.testing"
}

dependencies {
    // `api`, not `implementation`: consumers get JUnit and the coroutines test dispatchers
    // transitively, which is the whole point of depending on this module from a test source
    // set. Without it every module would still declare them itself.
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    // SessionContext / SessionPermissions, the types the shared builders construct.
    api("team.sakhi:SakhiCore:1.0.0")
}

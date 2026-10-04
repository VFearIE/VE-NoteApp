// CI 环境（GitHub Actions 注入 CI=true）直连官方源；本地默认走阿里云镜像。
// 注意：pluginManagement{} / dependencyResolutionManagement{} 在独立作用域求值，
// 不能引用 settings 脚本顶层变量（原写法会报 "Unresolved reference: isCI"），
// 因此把 isCI 分别定义在各块内部。
pluginManagement {
    val isCI = providers.environmentVariable("CI").getOrElse("false") == "true"
    repositories {
        if (isCI) {
            google()
            mavenCentral()
            gradlePluginPortal()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }
}

dependencyResolutionManagement {
    val isCI = providers.environmentVariable("CI").getOrElse("false") == "true"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (isCI) {
            google()
            mavenCentral()
        } else {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
            google()
            mavenCentral()
        }
    }
}

rootProject.name = "NoteApp"
include(":app")

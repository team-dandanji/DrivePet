pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            content { includeGroupByRegex("ai\\.pleos\\.playground(\\..*)?") }
        }
    }
}

rootProject.name = "DrivePet"
include(":vehicle")        // 차량 데이터 모듈 (팀원은 이 모듈만 의존)
include(":vehicle-demo")   // 디버그·시연용 앱 (가짜 데이터 조작, 시나리오 재생)
// 본 앱은 프론트엔드에서 :app 모듈로 추가 → implementation(project(":vehicle"))

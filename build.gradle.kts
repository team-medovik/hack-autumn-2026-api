import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Property
import org.jooq.meta.jaxb.Target
import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("nu.studer.jooq") version "10.1"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.medovik.hack2026"
version = "1.0.0"

val jooqVersion = "3.21.5"
val jwtVersion = "0.12.6"

val jooqSchemaScript = "src/main/resources/sql/init-tables.sql"

sourceSets.main {
    java.srcDir(layout.buildDirectory.dir("generated/jooq/main"))
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2025.1.3")
        mavenBom("tools.jackson:jackson-bom:3.2.4")
        mavenBom("com.fasterxml.jackson:jackson-bom:2.22.4")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // Web
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.yaml:snakeyaml")

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Security and JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("io.jsonwebtoken:jjwt-api:$jwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jwtVersion")

    // Flyway
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")

    // JOOQ
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jooq:jooq:$jooqVersion")
    jooqGenerator("org.postgresql:postgresql")
    jooqGenerator("org.jooq:jooq-codegen:$jooqVersion")
    jooqGenerator("org.jooq:jooq-meta:$jooqVersion")
    jooqGenerator("org.jooq:jooq-meta-extensions:$jooqVersion")
    runtimeOnly("org.postgresql:postgresql")

    // Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")

    // Logging
    implementation("org.springframework.boot:spring-boot-starter-log4j2")

    // Prometheus
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
}

configurations {
    all {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xannotation-default-target=param-property",
            "-Xjsr305=strict",
        )
    }
}

jooq {
    version.set(jooqVersion)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)

            jooqConfiguration.apply {
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"

                    database = Database().apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                        inputSchema = "PUBLIC"
                        isIncludeForeignKeys = false
                        properties = listOf(
                            Property().apply {
                                key = "scripts"
                                value = jooqSchemaScript
                            },
                            Property().apply {
                                key = "sort"
                                value = "semantic"
                            },
                            Property().apply {
                                key = "unqualifiedSchema"
                                value = "public"
                            },
                            Property().apply {
                                key = "defaultNameCase"
                                value = "lower"
                            },
                        )
                    }

                    generate = Generate().apply {
                        isRelations = false

                        isPojos = true
                        isRecords = true
                        isDaos = false
                        isJpaAnnotations = false

                        isJavaTimeTypes = true

                        // Генерировать Kotlin data class POJO
                        isPojosAsKotlinDataClasses = true
                        isImmutablePojos = true

                        // Сделать non-nullable свойства там, где столбец NOT NULL
                        isKotlinNotNullPojoAttributes = true
                        isKotlinNotNullInterfaceAttributes = true

                        // Отключить defaulted nullable (иначе все поля остаются nullable)
                        isKotlinDefaultedNullablePojoAttributes = false
                    }

                    target = Target().apply {
                        packageName = "ru.medovik.hack2026.api.jooq"
                        directory = "build/generated/jooq/main"
                    }
                }
            }
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(tasks.named("generateJooq"))
}

tasks.withType<Jar> {
    enabled = false
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    enabled = true
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<JavaExec>("riskReport") {
    group = "verification"
    description = "Расчёт риска: -PriskInput=JSON -PriskAt=ISO8601 -PriskOutput=JSON -PriskSettings=JSON"
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("ru.medovik.hack2026.api.service.risk.FixtureRiskReportKt")
    systemProperty("stdout.encoding", "UTF-8")
    systemProperty("stderr.encoding", "UTF-8")
    val inputPath = providers.gradleProperty("riskInput")
    val defaultReportName = if (inputPath.isPresent) "custom-risk.json" else "fixture-risk.json"
    val outputPath = providers.gradleProperty("riskOutput")
        .map { file(it).absolutePath }
        .getOrElse(layout.buildDirectory.file("reports/risk/$defaultReportName").get().asFile.absolutePath)
    args("--output", outputPath)
    inputPath.orNull?.let { args("--input", file(it).absolutePath) }
    providers.gradleProperty("riskAt").orNull?.let { args("--at", it) }
    providers.gradleProperty("riskSettings").orNull?.let { args("--settings", file(it).absolutePath) }
}

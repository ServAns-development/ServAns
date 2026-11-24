plugins {
    `java-library`
}

dependencies {
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.4.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

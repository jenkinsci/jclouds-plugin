#!/usr/bin/env groovy

String mavenCommand = 'mvn clean install -Dmaven.test.failure.ignore=true'
String testReports = '**/target/surefire-reports/**/*.xml'

Map platforms = [:]

platforms['windows'] = {
    node('windows') {
        checkout scm
        withEnv([
            "JAVA_HOME=${tool 'jdk7'}",
            "PATH+MAVEN=${tool 'mvn'}/bin",
        ]) {
            bat mavenCommand
        }
        junit testReports
    }
}

platforms['linux'] = {
    node('linux') {
        checkout scm
        withEnv([
            "JAVA_HOME=${tool 'jdk7'}",
            "PATH+MAVEN=${tool 'mvn'}/bin",
        ]) {
            sh mavenCommand
        }
        junit testReports
    }
}

stage 'build'
parallel(platforms)

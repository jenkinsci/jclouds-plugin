pipeline {
    agent {
      docker 'maven:3.2.5-jdk-8'
    }

    environment {
      MAVEN_OPTS = "-Xmx1024m"
    }

    stages {
        stage("Build") {
            steps {
              sh 'mvn -B -Dmaven.test.failure.ignore clean install'
            }
        }
    }

    post {
        success {
            archive "**/target/*.hpi"
            junit '**/target/surefire-reports/*.xml'
        }
    }

}

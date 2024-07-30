pipeline {
    agent any

    environment {
        envMode = getEnvMode()
        DOCKER_BUILDKIT = '1'
    }

    stages {

        stage('Build and Prepare Docker Image') {
            steps {
                script {
                    if (env.BRANCH_NAME == 'development') {
                        kafkaconnect = docker.build("hishabdev/kafka-connect-etl", "-f Dockerfile .",)
                    } else {
                        kafkaconnect = docker.build("hishabdev/kafka-connect-etl", "--no-cache -f Dockerfile .",)
                    }
                }
            }
        }

        stage('Push Docker Image') {
            steps {
                script {
                    docker.withRegistry('https://registry.hub.docker.com', 'docker_hub_login') {
                        kafkaconnect.push("${envMode}-4")
                    }
                }

            }
        }
    }
}

def getEnvMode() {
    if (env.BRANCH_NAME == 'development') {
        return "develop-etl"
    } else if (env.BRANCH_NAME == 'staging') {
        return "staging-etl"
    } else if (env.BRANCH_NAME == 'master') {
        return "master-etl"
    }
}
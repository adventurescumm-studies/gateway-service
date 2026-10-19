pipeline {
    // Agente Linux com Git, JDK 25, Maven e Docker Buildx no PATH.
    // Em jobs Pipeline comuns, configure o SCM para a branch main.
    agent any

    options {
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
    }

    environment {
        SERVICE = 'gateway'
        NAME = "weirdessen/${env.SERVICE}"
        MAVEN_REPO = "${env.WORKSPACE}/.m2/repository"
    }

    stages {
        // O gateway nao depende dos JARs account ou auth no pom.xml.
        stage('Build & Test') {
            steps {
                sh 'mvn -B -ntp -Dmaven.repo.local="$MAVEN_REPO" clean verify'
            }
        }

        stage('Build & Push Image') {
            when {
                // Multibranch: publica somente main, sem publicar pull requests.
                expression { !env.CHANGE_ID && (!env.BRANCH_NAME || env.BRANCH_NAME == 'main') }
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credential',
                    usernameVariable: 'DOCKER_USERNAME',
                    passwordVariable: 'DOCKER_TOKEN'
                )]) {
                    // Aspas simples deixam a expansao das credenciais para o shell.
                    sh '''
                        set +x
                        set -eu

                        build_dir="$(mktemp -d)"
                        builder=""
                        builder_created=false
                        cleanup() {
                            if [ "$builder_created" = true ]; then
                                docker buildx rm --force "$builder" >/dev/null 2>&1 || true
                            fi
                            rm -rf "$build_dir"
                        }
                        trap cleanup EXIT
                        trap 'exit 130' INT
                        trap 'exit 143' TERM

                        export DOCKER_CONFIG="$build_dir/docker"
                        builder="${SERVICE}-${BUILD_NUMBER}-$(basename "$build_dir" | tr '[:upper:]' '[:lower:]')"
                        mkdir -p "$DOCKER_CONFIG" "$build_dir/context/target"

                        # Envia apenas o Dockerfile e o JAR, sem o cache Maven.
                        cp Dockerfile "$build_dir/context/Dockerfile"
                        cp target/*.jar "$build_dir/context/target/"

                        printf '%s' "$DOCKER_TOKEN" | docker login --username "$DOCKER_USERNAME" --password-stdin
                        docker buildx create --name "$builder" --driver docker-container
                        builder_created=true

                        docker buildx build --builder "$builder" --platform linux/arm64,linux/amd64 --push --tag "$NAME:latest" --tag "$NAME:$BUILD_NUMBER" --file "$build_dir/context/Dockerfile" "$build_dir/context"
                    '''
                }
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
        }
    }
}

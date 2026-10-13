// Versao inicial baseada nos Jenkinsfiles fornecidos.
// Requer pom.xml e Dockerfile na raiz; confirme as premissas no LEIA-ME.txt.
pipeline {
    agent any // O agente selecionado precisa de Linux, JDK, Maven e Docker/Buildx.

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 60, unit: 'MINUTES')
    }

    parameters {
        booleanParam(name: 'SKIP_TESTS', defaultValue: true,
                     description: 'Mantem o comportamento dos exemplos; desmarque para executar os testes Maven.')
        booleanParam(name: 'BUILD_AUTH_DEPENDENCY', defaultValue: false,
                     description: 'Compila a biblioteca auth no mesmo agente antes deste servico. Confirme no pom.xml.')
        string(name: 'AUTH_BRANCH', defaultValue: 'main',
               description: 'Branch da biblioteca auth, usada apenas se BUILD_AUTH_DEPENDENCY estiver marcado.')
        string(name: 'GITHUB_CREDENTIALS_ID', defaultValue: 'github-repositories',
               description: 'ID real da credencial GitHub HTTPS para baixar auth; nao e o token nem a credencial Docker Hub.')
    }

    environment {
        SERVICE = 'gateway'
        DOCKER_IMAGE = 'weirdessen/gateway' // Confirme seu namespace e nome da imagem.
        DOCKER_PLATFORMS = 'linux/arm64,linux/amd64'
    }

    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                checkout scm // Usa a URL, a branch e a credencial GitHub configuradas no job.
                script {
                    // Exporta explicitamente o parametro, inclusive no primeiro build.
                    // A verificacao de null preserva false quando os testes forem habilitados.
                    env.SKIP_TESTS = (params.SKIP_TESTS == null ? true : params.SKIP_TESTS).toString()
                    // Fora do contexto Docker; biblioteca e servico usam este mesmo repositorio Maven.
                    env.MAVEN_REPO = "${pwd(tmp: true)}/maven-repository"
                    env.AUTH_SOURCE_DIR = "${pwd(tmp: true)}/auth-source"
                }
                sh '''
                    set -eu
                    test -f pom.xml || { echo 'Esperado pom.xml na raiz deste repositorio.' >&2; exit 1; }
                    test -f Dockerfile || { echo 'Esperado Dockerfile na raiz deste repositorio.' >&2; exit 1; }
                    java -version
                    docker info > /dev/null
                    docker buildx version
                '''
            }
        }

        stage('Preparar biblioteca auth') {
            when { expression { params.BUILD_AUTH_DEPENDENCY } }
            steps {
                dir(env.AUTH_SOURCE_DIR) {
                    deleteDir()
                    checkout scmGit(
                        branches: [[name: "*/${params.AUTH_BRANCH}"]],
                        userRemoteConfigs: [[
                            url: 'https://github.com/adventurescumm-studies/auth.git',
                            credentialsId: params.GITHUB_CREDENTIALS_ID
                        ]]
                    )
                    sh '''
                        set -eu
                        test -f pom.xml || { echo 'O repositorio auth precisa ser uma biblioteca Maven.' >&2; exit 1; }
                        if [ -f ./mvnw ]; then
                            sh ./mvnw -B "-Dmaven.repo.local=$MAVEN_REPO" "-DskipTests=${SKIP_TESTS:-true}" clean install
                        else
                            mvn -B "-Dmaven.repo.local=$MAVEN_REPO" "-DskipTests=${SKIP_TESTS:-true}" clean install
                        fi
                    '''
                }
            }
        }

        stage('Build Maven') {
            steps {
                sh '''
                    set -eu
                    if [ -f ./mvnw ]; then
                        sh ./mvnw -B "-Dmaven.repo.local=$MAVEN_REPO" "-DskipTests=${SKIP_TESTS:-true}" clean package
                    else
                        mvn -B "-Dmaven.repo.local=$MAVEN_REPO" "-DskipTests=${SKIP_TESTS:-true}" clean package
                    fi
                '''
            }
        }

        stage('Build e Push Docker') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'dockerhub-credential',
                    usernameVariable: 'DOCKERHUB_USERNAME',
                    passwordVariable: 'DOCKERHUB_TOKEN'
                )]) {
                    // Aspas simples: o shell expande os segredos, sem interpolacao do Groovy.
                    sh '''
                        set +x
                        set -eu
                        umask 077
                        ci_docker_config="$(mktemp -d)"
                        export DOCKER_CONFIG="$ci_docker_config"
                        ci_suffix="$(basename "$ci_docker_config" | tr '[:upper:]' '[:lower:]' | tr -cd 'a-z0-9')"
                        ci_builder="ci-${SERVICE}-${BUILD_NUMBER}-${ci_suffix}"

                        cleanup() {
                            docker buildx rm --force "$ci_builder" > /dev/null 2>&1 || true
                            rm -rf "$ci_docker_config"
                        }
                        trap cleanup EXIT
                        trap 'exit 130' INT
                        trap 'exit 143' TERM

                        printf '%s' "$DOCKERHUB_TOKEN" | docker login \
                            --username "$DOCKERHUB_USERNAME" --password-stdin

                        docker buildx create --driver docker-container --name "$ci_builder"
                        docker buildx inspect --builder "$ci_builder" --bootstrap
                        docker buildx build \
                            --builder "$ci_builder" \
                            --platform "$DOCKER_PLATFORMS" \
                            --tag "${DOCKER_IMAGE}:latest" \
                            --tag "${DOCKER_IMAGE}:${BUILD_NUMBER}" \
                            --file Dockerfile \
                            --push .
                    '''
                }
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: '**/target/*.jar,**/target/*.war',
                             fingerprint: true, allowEmptyArchive: true
        }
    }
}

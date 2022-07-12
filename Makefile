.PHONY: default

default: pull-request-ci

pull-request-ci: gradle-build
	cd infrastructure \
		&& npm run build \
		&& npx cdk synth -v

pipeline-build: gradle-build
	cd infrastructure \
		&& npm ci \
		&& npm run build

pipeline-synth:
	cd infrastructure \
		&& npx cdk synth

gradle-build:
	./gradlew build

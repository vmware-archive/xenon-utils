#!/bin/bash

# Prepare release for a xenon-utils project
#  - create a branch "prepare-release-${RELEASE_VERSION}"
#  - update CHANGELOG.md versions
#  - update pom versions
#  - create local commits for release and next dev versions
#

set -o pipefail
set -o nounset
set -o errexit


: ${BRANCH:=master}
COMMIT=$(git rev-parse HEAD)
CHANGE_LOG_FILE=CHANGELOG.md
UTILS_PROJECT=$(basename "$PWD")

case $(sed --version 2>&1) in
  *GNU*) SED_PARAMS="-i";;
  *) set SED_PARAMS="-i ''";;
esac

if [ "${NEXT_DEV_VERSION}:-" == "" ]; then
  echo Must specify NEXT_DEV_VERSION
  exit 1
elif ! echo ${NEXT_DEV_VERSION} | grep -- '-SNAPSHOT$' > /dev/null; then
  echo "NEXT_DEV_VERSION must be a -SNAPSHOT"
  exit 1
fi

XENON_LOCAL_REPO="."

# check clean or not
if [ -z "$(git status --porcelain)" ]; then
  # Working directory clean
  echo "${XENON_LOCAL_REPO} is clean"
else
  # Uncommitted changes
  echo "${XENON_LOCAL_REPO} is dirty"
  exit 1;
fi

git fetch origin

if ! git branch --contains ${COMMIT} -r | grep  origin/${BRANCH}; then
  echo "Commit ${COMMIT} not found in the remote branch. Cut a release only from commits that are already pushed"
  exit 1
fi

# compute release version from pom
CURRENT_VERSION=$(head -10 pom.xml | grep '<version>' | sed -e 's/^.*>\(.*\)<.*$/\1/')
RELEASE_VERSION=${CURRENT_VERSION%-SNAPSHOT}

echo Preparing release of ${RELEASE_VERSION} from ${CURRENT_VERSION}
echo You are going to release ${RELEASE_VERSION} from ${COMMIT}
git checkout -b prepare-release-${UTILS_PROJECT}-${RELEASE_VERSION}

# create release version
sed ${SED_PARAMS} -e "s/${CURRENT_VERSION}/${RELEASE_VERSION}/" ${CHANGE_LOG_FILE}
./mvnw versions:set -DgenerateBackupPoms=false -DnewVersion=${RELEASE_VERSION}
git commit -a -m "Mark ${UTILS_PROJECT}-${RELEASE_VERSION} for release"


# create next development version entry
sed ${SED_PARAMS} -e "1d" ${CHANGE_LOG_FILE}
sed ${SED_PARAMS} -e "1i\\
# CHANGELOG\\
\\
## ${NEXT_DEV_VERSION}\\
" ${CHANGE_LOG_FILE}
./mvnw versions:set -DgenerateBackupPoms=false -DnewVersion=${NEXT_DEV_VERSION}
git commit -a -m "Mark ${UTILS_PROJECT}-${NEXT_DEV_VERSION} for development"

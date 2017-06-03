#!/bin/bash

# Create branch and tag for a xenon-utils project, you must be in a subproject directory
#  - create branch and tag in gerrit
#  - need a reference to COMMIT to cut release from
#  - deployment to maven repo is not done by this script but by a CI job
#  - by default runs in dry-run mode (no remote interaction)

set -o pipefail
set -o nounset
set -o errexit

: ${DRY_RUN:=true}
UTILS_PROJECT=$(basename "$PWD")

if [ "${COMMIT:-}" == "" ]; then
  echo "Must specify COMMIT to create branch and tag from"
  exit 1
else
  echo "Cutting release from:"
fi

if ! git show --shortstat ${COMMIT} 2> /dev/null ; then
  echo "Commit ${COMMIT} not found: either a typo or you should fetch first"
  exit 1
fi
echo

# XXX: can brake
RELEASE_VERSION=$(git show ${COMMIT}:pom.xml | head -10 | grep '<version>' | sed -e 's/^.*>\(.*\)<.*$/\1/')

if echo ${RELEASE_VERSION} | grep -- '-SNAPSHOT$' > /dev/null; then
  echo "RELEASE_VERSION found in ${COMMIT} must be a release version, found ${RELEASE_VERSION}"
  exit 1
fi

RELEASE_BRANCH_NAME=${UTILS_PROJECT}-v${RELEASE_VERSION}
RELEASE_TAG_NAME=${RELEASE_BRANCH_NAME}-release


# create gerrit branch
echo "Creating branch ${RELEASE_BRANCH_NAME}"
if [ "$DRY_RUN" == "true" ]; then
  echo Would execute:
  echo ssh -p 29418 review.ec.eng.vmware.com gerrit create-branch xenon-utils ${RELEASE_BRANCH_NAME} ${COMMIT}
else
  ssh -p 29418 review.ec.eng.vmware.com gerrit create-branch xenon-utils ${RELEASE_BRANCH_NAME} ${COMMIT}
fi

echo
# create annotated tag
echo "Creating tag ${RELEASE_TAG_NAME}"
if [ "$DRY_RUN" == "true" ]; then
  echo Would execute:
  echo git tag -am "Tagging ${RELEASE_BRANCH_NAME}" ${RELEASE_TAG_NAME} ${COMMIT}
  echo git push origin ${RELEASE_TAG_NAME} HEAD:refs/heads/${RELEASE_BRANCH_NAME}
else
  git tag -am "Tagging ${RELEASE_BRANCH_NAME}" ${RELEASE_TAG_NAME} ${COMMIT}
  git push origin ${RELEASE_TAG_NAME} HEAD:refs/heads/${RELEASE_BRANCH_NAME}
fi



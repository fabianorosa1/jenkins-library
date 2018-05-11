import com.sap.piper.GitUtils
import groovy.transform.Field

import com.sap.piper.ConfigurationMerger
import com.sap.piper.cm.ChangeManagement
import com.sap.piper.cm.ChangeManagementException

@Field def STEP_NAME = 'checkChangeInDevelopment'

@Field Set parameterKeys = [
    'credentialsId',
    'endpoint',
    'failIfStatusIsNotInDevelopment',
    'git_from',
    'git_to',
    'git_label',
    'git_format'
  ]

@Field Set stepConfigurationKeys = [
    'credentialsId',
    'endpoint',
    'failIfStatusIsNotInDevelopment',
    'git_from',
    'git_to',
    'git_label',
    'git_format'
  ]

def call(parameters = [:]) {

    handlePipelineStepErrors (stepName: STEP_NAME, stepParameters: parameters) {

        System.err<<"PARAMS: ${parameters}\n"

        GitUtils gitUtils = parameters?.gitUtils ?: new GitUtils()

        ChangeManagement cm = parameters?.cmUtils ?: new ChangeManagement(parameters.script, gitUtils)

        System.err<<"CM: ${cm}"

        Map configuration = ConfigurationMerger.merge(parameters.script, STEP_NAME,
                                                      parameters, parameterKeys,
                                                      stepConfigurationKeys)


        def changeId

        try {
            changeId = cm.getChangeDocumentId(
                                              configuration.git_from,
                                              configuration.git_to,
                                              configuration.git_label,
                                              configuration.git_format
                                            )
        } catch(ChangeManagementException ex) {
            throw new hudson.AbortException(ex.getMessage())
        }

        echo "[INFO] [${STEP_NAME}] ChangeId retrieved from git commit(s): '${changeId}'. " +
             "Commit range: '${configuration.git_from}..${configuration.git_to}'. " +
             "Searching for label '${configuration.git_label}'."

        boolean isInDevelopment

        withCredentials([usernamePassword(
            credentialsId: configuration.credentialsId,
            passwordVariable: 'password',
            usernameVariable: 'username')]) {

            try {
                isInDevelopment = cm.isChangeInDevelopment(changeId, configuration.endpoint, username, password)
            } catch(ChangeManagementException ex) {
                throw new hudson.AbortException(ex.getMessage())
            }
        }

        if(isInDevelopment) {
            echo "[INFO] [${STEP_NAME}] Change '${changeId}' is in status 'in development'."
        } else {
            if(configuration.failIfStatusIsNotInDevelopment.toBoolean()) {
                throw new hudson.AbortException("Change '${changeId}' is not in status 'in development'.")

            } else {
                echo "[WARNING] [${STEP_NAME}] Change '${changeId}' is not in status 'in development'. Failing the pipeline has been explicitly disabled."
            }
        }
    }
}

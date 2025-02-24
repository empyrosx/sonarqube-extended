/*
 * SonarQube
 * Copyright (C) 2009-2022 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
import * as React from 'react';
import { FormattedMessage } from 'react-intl';
import { Link } from 'react-router';
import withAppStateContext from '../../../../app/components/app-state/withAppStateContext';
import { Alert } from '../../../../components/ui/Alert';
import { ALM_DOCUMENTATION_PATHS } from '../../../../helpers/constants';
import { translate } from '../../../../helpers/l10n';
import { AlmKeys } from '../../../../types/alm-settings';
import { AppState } from '../../../../types/appstate';
import SentenceWithHighlights from '../../components/SentenceWithHighlights';

export interface PublishStepsProps {
  appState: AppState;
}
export function PublishSteps(props: PublishStepsProps) {
  const {
    appState: { branchesEnabled }
  } = props;

  return (
    <>
      <li>
        <SentenceWithHighlights
          translationKey="onboarding.tutorial.with.azure_pipelines.BranchAnalysis.publish_qg"
          highlightKeys={['task']}
        />
        <Alert variant="info" className="spacer-top">
          {translate(
            'onboarding.tutorial.with.azure_pipelines.BranchAnalysis.publish_qg.info.sentence1'
          )}
        </Alert>
      </li>
      <li>
        <SentenceWithHighlights
          translationKey={
            branchesEnabled
              ? 'onboarding.tutorial.with.azure_pipelines.BranchAnalysis.continous_integration'
              : 'onboarding.tutorial.with.azure_pipelines.BranchAnalysis.continous_integration.no_branches'
          }
          highlightKeys={['tab', 'continuous_integration']}
        />
      </li>
      {branchesEnabled && (
        <>
          <hr />
          <FormattedMessage
            id="onboarding.tutorial.with.azure_pipelines.BranchAnalysis.branch_protection"
            defaultMessage={translate(
              'onboarding.tutorial.with.azure_pipelines.BranchAnalysis.branch_protection'
            )}
            values={{
              link: (
                <Link to={ALM_DOCUMENTATION_PATHS[AlmKeys.Azure]} target="_blank">
                  {translate(
                    'onboarding.tutorial.with.azure_pipelines.BranchAnalysis.branch_protection.link'
                  )}
                </Link>
              )
            }}
          />
        </>
      )}
    </>
  );
}

export default withAppStateContext(PublishSteps);

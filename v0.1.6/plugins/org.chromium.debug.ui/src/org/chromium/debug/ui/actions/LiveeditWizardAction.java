// Copyright (c) 2010 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.debug.ui.actions;

import java.util.List;

import org.chromium.debug.core.util.ScriptTargetMapping;
import org.chromium.debug.ui.liveedit.PushChangesWizard;
import org.eclipse.swt.widgets.Shell;


/**
 * An action that opens a full-featured wizard for pushing changes to remote V8 VM.
 */
public class LiveeditWizardAction extends V8ScriptAction {
  @Override
  protected void execute(List<? extends ScriptTargetMapping> filePairList, Shell shell) {
    PushChangesWizard.start(filePairList, shell);
  }
}
/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.transformations.impl.synch

import com.intellij.psi.PsiElement
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.usageView.UsageInfo
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.refactoring.rename.RenameGrFieldProcessor

class SynchronizedRenameFieldProceccor : RenameGrFieldProcessor() {

  override fun canProcessElement(element: PsiElement): Boolean {
    return super.canProcessElement(element) && getMethodsImplicitlyReferencingLock(element as GrField).isNotEmpty()
  }

  override fun renameElement(element: PsiElement,
                             newName: String,
                             usages: Array<out UsageInfo>,
                             listener: RefactoringElementListener?) {
    element as GrField
    val value = GroovyPsiElementFactory.getInstance(element.project).createLiteralFromValue(newName)
    getMethodsImplicitlyReferencingLock(element).forEach {
      it.second.setDeclaredAttributeValue(null, value)
    }
    super.renameElement(element, newName, usages, listener)
  }
}
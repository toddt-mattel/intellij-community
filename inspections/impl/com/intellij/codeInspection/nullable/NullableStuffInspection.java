package com.intellij.codeInspection.nullable;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.BaseLocalInspectionTool;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

public class NullableStuffInspection extends BaseLocalInspectionTool {
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_GETTER = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_NOT_ANNOTATED_SETTER_PARAMETER = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = true;
  private static final AnnotateMethodFix ANNOTATE_OVERRIDDEN_METHODS_FIX = new AnnotateMethodFix(AnnotationUtil.NOT_NULL){
    protected boolean annotateOverriddenMethods() {
      return true;
    }

    @NotNull
    public String getName() {
      return InspectionsBundle.message("annotate.overridden.methods.as.notnull");
    }
  };
  private static final AnnotateOverriddenMethodParameterFix ANNOTATE_OVERRIDDEN_METHODS_PARAMS_FIX =
    new AnnotateOverriddenMethodParameterFix(AnnotationUtil.NOT_NULL);

  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {

      }

      public void visitMethod(PsiMethod method) {
        checkNullableStuffForMethod(method, holder);
      }

      public void visitField(PsiField field) {
        boolean isDeclaredNotNull = AnnotationUtil.isAnnotated(field, AnnotationUtil.NOT_NULL, false);
        boolean isDeclaredNullable = AnnotationUtil.isAnnotated(field, AnnotationUtil.NULLABLE, false);
        if (isDeclaredNullable && isDeclaredNotNull) {
          reportNullableNotNullConflict(holder, field.getNameIdentifier());
        }
        if ((isDeclaredNotNull || isDeclaredNullable) && TypeConversionUtil.isPrimitive(field.getType().getCanonicalText())) {
          reportPrimitiveType(holder, field.getNameIdentifier());
        }
        if (isDeclaredNotNull || isDeclaredNullable) {
          final String anno = isDeclaredNotNull ? AnnotationUtil.NOT_NULL : AnnotationUtil.NULLABLE;
          final String simpleName = isDeclaredNotNull ? AnnotationUtil.NOT_NULL_SIMPLE_NAME : AnnotationUtil.NULLABLE_SIMPLE_NAME;

          final String propName = field.getManager().getCodeStyleManager().variableNameToPropertyName(field.getName(), VariableKind.FIELD);
          final boolean isStatic = field.hasModifierProperty(PsiModifier.STATIC);
          if (REPORT_NOT_ANNOTATED_GETTER) {
            final PsiMethod getter = PropertyUtil.findPropertyGetter(field.getContainingClass(), propName, isStatic, false);
            if (getter != null) {
              final boolean getterAnnotated = AnnotationUtil.findAnnotation(getter, AnnotationUtil.NULLABLE, AnnotationUtil.NOT_NULL) != null;
              if (!getterAnnotated) {
                holder.registerProblem(getter.getNameIdentifier(),
                                 InspectionsBundle.message("inspection.nullable.problems.annotated.field.getter.not.annotated", simpleName),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new AnnotateMethodFix(anno));
              }
            }
          }

          if (REPORT_NOT_ANNOTATED_SETTER_PARAMETER) {
            final PsiMethod setter = PropertyUtil.findPropertySetter(field.getContainingClass(), propName, isStatic, false);
            if (setter != null) {
              final PsiParameter[] parameters = setter.getParameterList().getParameters();
              assert parameters.length == 1;
              final PsiParameter parameter = parameters[0];
              final boolean parameterAnnotated = AnnotationUtil.findAnnotation(parameter, AnnotationUtil.NULLABLE, AnnotationUtil.NOT_NULL) != null;
              if (!parameterAnnotated) {
                holder.registerProblem(parameter.getNameIdentifier(),
                                 InspectionsBundle.message("inspection.nullable.problems.annotated.field.setter.parameter.not.annotated", simpleName),
                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                 new AnnotateQuickFix(parameter, anno, simpleName));
              }
            }
          }
        }
      }

      public void visitParameter(PsiParameter parameter) {
        boolean isDeclaredNotNull = AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NOT_NULL, false);
        boolean isDeclaredNullable = AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NULLABLE, false);
        if (isDeclaredNullable && isDeclaredNotNull) {
          reportNullableNotNullConflict(holder, parameter.getNameIdentifier());
        }
        if ((isDeclaredNotNull || isDeclaredNullable) && TypeConversionUtil.isPrimitive(parameter.getType().getCanonicalText())) {
          reportPrimitiveType(holder, parameter.getNameIdentifier());
        }
      }
    };
  }

  private static void reportPrimitiveType(final ProblemsHolder holder, final PsiElement psiElement) {
    holder.registerProblem(psiElement,
                           InspectionsBundle.message("inspection.nullable.problems.primitive.type.annotation"),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING);

  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.nullable.problems.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @NotNull
  public String getShortName() {
    return "NullableProblems";
  }

  private void checkNullableStuffForMethod(PsiMethod method, final ProblemsHolder holder) {
    boolean isDeclaredNotNull = AnnotationUtil.isAnnotated(method, AnnotationUtil.NOT_NULL, false);
    boolean isDeclaredNullable = AnnotationUtil.isAnnotated(method, AnnotationUtil.NULLABLE, false);
    if (isDeclaredNullable && isDeclaredNotNull) {
      reportNullableNotNullConflict(holder, method.getNameIdentifier());
    }
    PsiType returnType = method.getReturnType();
    if ((isDeclaredNotNull || isDeclaredNullable) && returnType != null && TypeConversionUtil.isPrimitive(returnType.getCanonicalText())) {
      reportPrimitiveType(holder, method.getReturnTypeElement());
    }

    PsiParameter[] parameters = method.getParameterList().getParameters();

    List<MethodSignatureBackedByPsiMethod> superMethodSignatures = method.findSuperMethodSignaturesIncludingStatic(true);
    boolean reported_not_annotated_method_overrides_notnull = false;
    boolean reported_nullable_method_overrides_notnull = false;
    boolean[] reported_notnull_parameter_overrides_nullable = new boolean[parameters.length];
    boolean[] reported_not_annotated_parameter_overrides_notnull = new boolean[parameters.length];

    for (MethodSignatureBackedByPsiMethod superMethodSignature : superMethodSignatures) {
      PsiMethod superMethod = superMethodSignature.getMethod();
      if (!reported_nullable_method_overrides_notnull
          && REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL
          && isDeclaredNullable
          && AnnotationUtil.isNotNull(superMethod)
        ) {
        reported_nullable_method_overrides_notnull = true;
        holder.registerProblem(method.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.Nullable.method.overrides.NotNull"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
      if (!reported_not_annotated_method_overrides_notnull
          && REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL
          && !isDeclaredNullable
          && !isDeclaredNotNull
          && AnnotationUtil.isNotNull(superMethod)) {
        reported_not_annotated_method_overrides_notnull = true;
        holder.registerProblem(method.getNameIdentifier(),
                               InspectionsBundle.message("inspection.nullable.problems.method.overrides.NotNull"),
                               ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                               new AnnotateMethodFix(AnnotationUtil.NOT_NULL){
                                 public int annotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
                                   return NullableStuffInspection.this.annotateBaseMethod(method, superMethod, project);
                                 }
                               });
      }
      if (REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE || REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL) {
        PsiParameter[] superParameters = superMethod.getParameterList().getParameters();
        if (superParameters.length != parameters.length) {
          continue;
        }
        for (int i = 0; i < parameters.length; i++) {
          PsiParameter parameter = parameters[i];
          PsiParameter superParameter = superParameters[i];
          if (!reported_notnull_parameter_overrides_nullable[i]
              && REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE
              && AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NOT_NULL, false)
              && AnnotationUtil.isAnnotated(superParameter, AnnotationUtil.NULLABLE, false)) {
            reported_notnull_parameter_overrides_nullable[i] = true;
            holder.registerProblem(parameter.getNameIdentifier(),
                                   InspectionsBundle.message("inspection.nullable.problems.NotNull.parameter.overrides.Nullable"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
          if (!reported_not_annotated_parameter_overrides_notnull[i]
              && REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL
              && !AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NOT_NULL, false)
              && !AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NULLABLE, false)
              && AnnotationUtil.isAnnotated(superParameter, AnnotationUtil.NOT_NULL, false)) {
            reported_not_annotated_parameter_overrides_notnull[i] = true;
            holder.registerProblem(parameter.getNameIdentifier(),
                                   InspectionsBundle.message("inspection.nullable.problems.parameter.overrides.NotNull"),
                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   new AnnotateQuickFix(parameter, AnnotationUtil.NOT_NULL, AnnotationUtil.NOT_NULL_SIMPLE_NAME));
          }
        }
      }
    }

    if (REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS) {
      boolean[] parameterAnnotated = new boolean[parameters.length];
      boolean[] parameterQuickFixSuggested = new boolean[parameters.length];
      boolean hasAnnotatedParameter = false;
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        parameterAnnotated[i] = AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NOT_NULL, false);
        hasAnnotatedParameter |= parameterAnnotated[i];
      }
      if (hasAnnotatedParameter || isDeclaredNotNull) {
        PsiManager manager = method.getManager();
        PsiMethod[] overridings = manager.getSearchHelper().findOverridingMethods(method, GlobalSearchScope.allScope(manager.getProject()), true);
        boolean methodQuickFixSuggested = false;
        for (PsiMethod overriding : overridings) {
          if (!manager.isInProject(overriding)) continue;
          if (!methodQuickFixSuggested
              && isDeclaredNotNull 
              && !AnnotationUtil.isAnnotated(overriding, AnnotationUtil.NOT_NULL, false)) {
            PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, AnnotationUtil.NOT_NULL);
            holder.registerProblem(annotation, InspectionsBundle.message("nullable.stuff.problems.overridden.methods.are.not.annotated"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                   ANNOTATE_OVERRIDDEN_METHODS_FIX);
            methodQuickFixSuggested = true;
          }
          if (hasAnnotatedParameter) {
            PsiParameter[] psiParameters = overriding.getParameterList().getParameters();
            for (int i = 0; i < psiParameters.length; i++) {
              if (parameterQuickFixSuggested[i]) continue;
              PsiParameter parameter = psiParameters[i];
              if (parameterAnnotated[i]
                  && !AnnotationUtil.isAnnotated(parameter, AnnotationUtil.NOT_NULL, false)) {
                PsiAnnotation annotation = AnnotationUtil.findAnnotation(parameters[i], AnnotationUtil.NOT_NULL);
                holder.registerProblem(annotation,
                                       InspectionsBundle.message("nullable.stuff.problems.overridden.method.parameters.are.not.annotated"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                       ANNOTATE_OVERRIDDEN_METHODS_PARAMS_FIX);
                parameterQuickFixSuggested[i] = true;
              }
            }
          }
        }
      }
    }
  }

  protected int annotateBaseMethod(final PsiMethod method, final PsiMethod superMethod, final Project project) {
    return new AnnotateMethodFix(AnnotationUtil.NOT_NULL).annotateBaseMethod(method, superMethod, project);
  }

  private static void reportNullableNotNullConflict(final ProblemsHolder holder, PsiIdentifier psiElement) {
    holder.registerProblem(psiElement,
                           InspectionsBundle.message("inspection.nullable.problems.Nullable.NotNull.conflict"),
                           ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
  }

  public JComponent createOptionsPanel() {
    return new OptionsPanel();
  }

  private class OptionsPanel extends JPanel {
    private JCheckBox myNNParameterOverridesN;
    private JCheckBox myNAMethodOverridesNN;
    private JCheckBox myNMethodOverridesNN;
    private JCheckBox myNAParameterOverridesNN;
    private JPanel myPanel;
    private JCheckBox myReportNotAnnotatedSetterParameter;
    private JCheckBox myReportNotAnnotatedGetter;
    private JCheckBox myReportAnnotationNotPropagated;

    private OptionsPanel() {
      super(new BorderLayout());
      add(myPanel, BorderLayout.CENTER);

      ActionListener actionListener = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          apply();
        }
      };
      myNAMethodOverridesNN.addActionListener(actionListener);
      myNMethodOverridesNN.addActionListener(actionListener);
      myNNParameterOverridesN.addActionListener(actionListener);
      myNAParameterOverridesNN.addActionListener(actionListener);
      myReportNotAnnotatedSetterParameter.addActionListener(actionListener);
      myReportNotAnnotatedGetter.addActionListener(actionListener);
      myReportAnnotationNotPropagated.addActionListener(actionListener);
      reset();
    }

    private void reset() {
      myNNParameterOverridesN.setSelected(REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE);
      myNAMethodOverridesNN.setSelected(REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL);
      myNMethodOverridesNN.setSelected(REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL);
      myNAParameterOverridesNN.setSelected(REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL);
      myReportNotAnnotatedGetter.setSelected(REPORT_NOT_ANNOTATED_GETTER);
      myReportNotAnnotatedSetterParameter.setSelected(REPORT_NOT_ANNOTATED_SETTER_PARAMETER);
      myReportAnnotationNotPropagated.setSelected(REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS);
    }

    private void apply() {
      REPORT_NOT_ANNOTATED_METHOD_OVERRIDES_NOTNULL = myNAMethodOverridesNN.isSelected();
      REPORT_NOTNULL_PARAMETER_OVERRIDES_NULLABLE = myNNParameterOverridesN.isSelected();
      REPORT_NULLABLE_METHOD_OVERRIDES_NOTNULL = myNMethodOverridesNN.isSelected();
      REPORT_NOT_ANNOTATED_PARAMETER_OVERRIDES_NOTNULL = myNAParameterOverridesNN.isSelected();
      REPORT_NOT_ANNOTATED_SETTER_PARAMETER = myReportNotAnnotatedSetterParameter.isSelected();
      REPORT_NOT_ANNOTATED_GETTER = myReportNotAnnotatedGetter.isSelected();
      REPORT_ANNOTATION_NOT_PROPAGATED_TO_OVERRIDERS = myReportAnnotationNotPropagated.isSelected();
    }
  }
}

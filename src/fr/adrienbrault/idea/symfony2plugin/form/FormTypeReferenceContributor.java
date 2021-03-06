package fr.adrienbrault.idea.symfony2plugin.form;

import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.PhpIndex;
import com.jetbrains.php.lang.parser.PhpElementTypes;
import com.jetbrains.php.lang.psi.elements.*;
import com.jetbrains.php.lang.psi.elements.impl.PhpTypedElementImpl;
import fr.adrienbrault.idea.symfony2plugin.Symfony2InterfacesUtil;
import fr.adrienbrault.idea.symfony2plugin.Symfony2ProjectComponent;
import fr.adrienbrault.idea.symfony2plugin.doctrine.EntityReference;
import fr.adrienbrault.idea.symfony2plugin.form.util.FormFieldNameReference;
import fr.adrienbrault.idea.symfony2plugin.form.util.FormUtil;
import fr.adrienbrault.idea.symfony2plugin.translation.TranslationDomainReference;
import fr.adrienbrault.idea.symfony2plugin.translation.TranslationReference;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import fr.adrienbrault.idea.symfony2plugin.util.ParameterBag;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.PsiElementUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class FormTypeReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar psiReferenceRegistrar) {

        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class)
                .withParent(
                    PlatformPatterns.psiElement(PhpElementTypes.ARRAY_VALUE).inside(ParameterList.class)
                ),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {
                    if (!Symfony2ProjectComponent.isEnabled(psiElement)) {
                        return new PsiReference[0];
                    }

                    ParameterList parameterList = PsiTreeUtil.getParentOfType(psiElement, ParameterList.class);
                    if (parameterList == null) {
                        return new PsiReference[0];
                    }

                    if(!(parameterList.getContext() instanceof MethodReference)) {
                        return new PsiReference[0];
                    }

                    MethodReference method = (MethodReference) parameterList.getContext();
                    Symfony2InterfacesUtil interfacesUtil = new Symfony2InterfacesUtil();
                    if (!interfacesUtil.isFormBuilderFormTypeCall(method)) {
                        return new PsiReference[0];
                    }

                    ArrayHashElement arrayHash = PsiTreeUtil.getParentOfType(psiElement, ArrayHashElement.class);
                    if(arrayHash != null && arrayHash.getKey() instanceof StringLiteralExpression) {

                        ArrayCreationExpression arrayCreation = PsiTreeUtil.getParentOfType(psiElement, ArrayCreationExpression.class);
                        if(arrayCreation == null) {
                            return new PsiReference[0];
                        }

                        // old 3 parameter hold valid array data
                        ParameterBag currentIndex = PsiElementUtils.getCurrentParameterIndex(arrayCreation);
                        if(currentIndex == null || currentIndex.getIndex() != 2) {
                            return new PsiReference[0];
                        }

                        StringLiteralExpression key = (StringLiteralExpression) arrayHash.getKey();
                        if(key == null) {
                            return new PsiReference[0];
                        }

                        String keyString = key.getContents();

                        if(keyString.equals("translation_domain")) {
                            return new PsiReference[]{ new TranslationDomainReference((StringLiteralExpression) psiElement) };
                        }

                        // @TODO: how to handle custom bundle fields like help_block
                        if(keyString.equals("label") || keyString.equals("help_block") || keyString.equals("help_inline")) {

                            // translation_domain in current array block
                            String translationDomain = PhpElementsUtil.getArrayHashValue(arrayCreation, "translation_domain");
                            if(translationDomain == null) {
                                translationDomain = PhpElementsUtil.getArrayKeyValueInsideSignature(psiElement, "setDefaultOptions",  "setDefaults", "translation_domain");
                            }

                            if(translationDomain == null) {
                                translationDomain = "messages";
                            }

                            return new PsiReference[]{ new TranslationReference((StringLiteralExpression) psiElement, translationDomain) };
                        }

                        if(keyString.equals("class")) {
                            return new PsiReference[]{ new EntityReference((StringLiteralExpression) psiElement, true)};
                        }

                    }

                    return new PsiReference[0];

                }

            }

        );

        /**
         * FormBuilderInterface:add("", "type")
         */
        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {

                    MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.StringParameterMatcher(psiElement, 1)
                        .withSignature(Symfony2InterfacesUtil.getFormBuilderInterface())
                        .match();

                    if(methodMatchParameter == null) {
                        return new PsiReference[0];
                    }

                    return new PsiReference[]{ new FormTypeReference((StringLiteralExpression) psiElement) };

                }

            }

        );

        // FormBuilderInterface::add('underscore_method')
        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {
                    if (!Symfony2ProjectComponent.isEnabled(psiElement) || !(psiElement.getContext() instanceof ParameterList)) {
                        return new PsiReference[0];
                    }

                    ParameterList parameterList = (ParameterList) psiElement.getContext();

                    if (parameterList == null || !(parameterList.getContext() instanceof MethodReference)) {
                        return new PsiReference[0];
                    }

                    MethodReference method = (MethodReference) parameterList.getContext();
                    if (method == null || !new Symfony2InterfacesUtil().isFormBuilderFormTypeCall(method)) {
                        return new PsiReference[0];
                    }

                    // only use second parameter
                    ParameterBag currentIndex = PsiElementUtils.getCurrentParameterIndex(psiElement);
                    if(currentIndex == null || currentIndex.getIndex() != 0) {
                        return new PsiReference[0];
                    }

                    String className = PhpElementsUtil.getArrayKeyValueInsideSignature(psiElement, "setDefaultOptions",  "setDefaults", "data_class");
                    if(className != null) {
                        PhpClass dataClass = PhpElementsUtil.getClass(PhpIndex.getInstance(psiElement.getProject()), className);
                        if(dataClass != null) {
                            return new PsiReference[]{ new FormUnderscoreMethodReference((StringLiteralExpression) psiElement, dataClass) };
                        }
                    }

                    return new PsiReference[0];

                }

            }

        );

        // FormTypeInterface::getParent
        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext context) {

                    if (!Symfony2ProjectComponent.isEnabled(psiElement)) {
                        return new PsiReference[0];
                    }

                    if(!(psiElement instanceof StringLiteralExpression) || !PhpElementsUtil.getMethodReturnPattern().accepts(psiElement)) {
                        return new PsiReference[0];
                    }

                    Method method = PsiTreeUtil.getParentOfType(psiElement, Method.class);
                    if(method == null) {
                        return new PsiReference[0];
                    }

                    if(!new Symfony2InterfacesUtil().isCallTo(method, "\\Symfony\\Component\\Form\\FormTypeInterface", "getParent")) {
                        return new PsiReference[0];
                    }

                    return new PsiReference[]{ new FormTypeReference((StringLiteralExpression) psiElement) };
                }
            }
        );

        // FormBuilderInterface::add('underscore_method')
        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {
                    if (!Symfony2ProjectComponent.isEnabled(psiElement)) {
                        return new PsiReference[0];
                    }

                    ParameterList parameterList = PsiTreeUtil.getParentOfType(psiElement, ParameterList.class);
                    if (parameterList == null) {
                        return new PsiReference[0];
                    }

                    if(!(parameterList.getContext() instanceof MethodReference)) {
                        return new PsiReference[0];
                    }

                    MethodReference method = (MethodReference) parameterList.getContext();
                    Symfony2InterfacesUtil interfacesUtil = new Symfony2InterfacesUtil();
                    if (!interfacesUtil.isCallTo(method, "\\Symfony\\Component\\OptionsResolver\\OptionsResolverInterface", "setDefaults")) {
                        return new PsiReference[0];
                    }

                    // only use second parameter
                    ArrayCreationExpression arrayHash = PsiTreeUtil.getParentOfType(psiElement, ArrayCreationExpression.class);
                    if(arrayHash == null) {
                        return new PsiReference[0];
                    }

                    ParameterBag currentIndex = PsiElementUtils.getCurrentParameterIndex(arrayHash);
                    if(currentIndex == null || currentIndex.getIndex() != 0) {
                        return new PsiReference[0];
                    }

                    if(PhpElementsUtil.getCompletableArrayCreationElement(psiElement) != null) {
                        return new PsiReference[]{
                            new FormExtensionKeyReference((StringLiteralExpression) psiElement),
                            new FormDefaultOptionsKeyReference((StringLiteralExpression) psiElement, "form")
                        };
                    }

                    return new PsiReference[0];
                }

            }

        );

        // $form->get('field')
        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {


                    MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.StringParameterMatcher(psiElement, 0)
                        .withSignature("\\Symfony\\Component\\Form\\FormInterface", "get")
                        .withSignature("\\Symfony\\Component\\Form\\FormInterface", "has")
                        .match();

                    if(methodMatchParameter == null) {
                        return new PsiReference[0];
                    }

                    Method method = FormUtil.resolveFormGetterCallMethod(methodMatchParameter.getMethodReference());
                    if(method == null) {
                        return new PsiReference[0];
                    }

                    return new PsiReference[] {
                        new FormFieldNameReference((StringLiteralExpression) psiElement, method)
                    };
                }

            }

        );

        /**
         * $this->createForm(new FormType(), $entity, array('<foo_key>' => ''));
         * $this->createForm('foo', $entity, array('<foo_key>'));
         */
        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {

                    MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.ArrayParameterMatcher(psiElement, 2)
                        .withSignature("\\Symfony\\Bundle\\FrameworkBundle\\Controller\\Controller", "createForm")
                        .withSignature("\\Symfony\\Component\\Form\\FormFactoryInterface", "create")
                        .withSignature("\\Symfony\\Component\\Form\\FormFactory", "createBuilder")
                        .match();

                    if(methodMatchParameter == null) {
                        return new PsiReference[0];
                    }

                    return getFormPsiReferences((StringLiteralExpression) psiElement, methodMatchParameter.getParameters()[0]);

                }

            }

        );

        /**
         * $options lookup
         * public function createNamedBuilder($name, $type = 'form', $data = null, array $options = array())
         */
        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {

                    MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.ArrayParameterMatcher(psiElement, 3)
                        .withSignature("\\Symfony\\Component\\Form\\FormFactoryInterface", "createNamedBuilder")
                        .withSignature("\\Symfony\\Component\\Form\\FormFactoryInterface", "createNamed")
                        .match();

                    if(methodMatchParameter == null) {
                        return new PsiReference[0];
                    }

                    return getFormPsiReferences((StringLiteralExpression) psiElement, methodMatchParameter.getParameters()[1]);

                }

            }

        );

        /**
         * $type lookup
         * public function createNamedBuilder($name, $type = 'form', $data = null, array $options = array())
         */
        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {

                    MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.StringParameterMatcher(psiElement, 1)
                        .withSignature("\\Symfony\\Component\\Form\\FormFactoryInterface", "createNamedBuilder")
                        .withSignature("\\Symfony\\Component\\Form\\FormFactoryInterface", "createNamed")
                        .match();

                    if(methodMatchParameter == null) {
                        return new PsiReference[0];
                    }

                    return new PsiReference[]{ new FormTypeReference((StringLiteralExpression) psiElement) };

                }

            }

        );

        /**
         * $options
         * public function buildForm(FormBuilderInterface $builder, array $options) {
         *   $options['foo']
         * }
         *
         * public function setDefaultOptions(OptionsResolverInterface $resolver) {
         *   $resolver->setDefaults(array(
         *    'foo' => 'bar',
         * ));
         }

         */
        psiReferenceRegistrar.registerReferenceProvider(
            PlatformPatterns.psiElement(StringLiteralExpression.class),
            new PsiReferenceProvider() {
                @NotNull
                @Override
                public PsiReference[] getReferencesByElement(@NotNull PsiElement psiElement, @NotNull ProcessingContext processingContext) {

                    if (!Symfony2ProjectComponent.isEnabled(psiElement)) {
                        return new PsiReference[0];
                    }

                    PsiElement context = psiElement.getContext();
                    if(!(context instanceof ArrayIndex)) {
                        return new PsiReference[0];
                    }

                    PhpPsiElement variable = ((ArrayIndex) context).getPrevPsiSibling();
                    if(!(variable instanceof Variable)) {
                        return new PsiReference[0];
                    }

                    PsiElement parameter = ((Variable) variable).resolve();

                    if(!(parameter instanceof Parameter)) {
                        return new PsiReference[0];
                    }

                    // all options keys are at parameter = 1 by now
                    ParameterBag parameterBag = PsiElementUtils.getCurrentParameterIndex((Parameter) parameter);
                    if(parameterBag == null || parameterBag.getIndex() != 1) {
                        return new PsiReference[0];
                    }

                    Method method = PsiTreeUtil.getParentOfType(parameter, Method.class);
                    if(method == null) {
                        return new PsiReference[0];
                    }

                    Symfony2InterfacesUtil symfony2InterfacesUtil = new Symfony2InterfacesUtil();
                    if(!symfony2InterfacesUtil.isCallTo(method, "\\Symfony\\Component\\Form\\FormTypeInterface", "buildForm") &&
                        !symfony2InterfacesUtil.isCallTo(method, "\\Symfony\\Component\\Form\\FormTypeInterface", "buildView") &&
                        !symfony2InterfacesUtil.isCallTo(method, "\\Symfony\\Component\\Form\\FormTypeInterface", "finishView"))
                    {
                        return new PsiReference[0];
                    }

                    PhpClass phpClass = method.getContainingClass();
                    if(phpClass == null) {
                        return new PsiReference[0];
                    }

                    return new PsiReference[]{
                        new FormExtensionKeyReference((StringLiteralExpression) psiElement),
                        new FormDefaultOptionsKeyReference((StringLiteralExpression) psiElement, phpClass.getPresentableFQN())
                    };

                }

            }

        );


    }

    private PsiReference[] getFormPsiReferences(StringLiteralExpression psiElement, PsiElement formType) {
        PhpClass phpClass = FormUtil.getFormTypeClassOnParameter(formType);
        if (phpClass == null) {
            return new PsiReference[]{
                new FormExtensionKeyReference(psiElement, "form")
            };
        }

        return new PsiReference[]{
            new FormExtensionKeyReference(psiElement, phpClass.getPresentableFQN()),
            new FormDefaultOptionsKeyReference(psiElement, phpClass.getPresentableFQN())
        };
    }

}

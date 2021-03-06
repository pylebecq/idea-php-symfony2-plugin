package fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.PhpIcons;
import com.jetbrains.php.lang.psi.elements.MethodReference;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import fr.adrienbrault.idea.symfony2plugin.Symfony2Icons;
import fr.adrienbrault.idea.symfony2plugin.Symfony2ProjectComponent;
import fr.adrienbrault.idea.symfony2plugin.doctrine.dict.DoctrineModelField;
import fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder.dict.QueryBuilderPropertyAlias;
import fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder.dict.QueryBuilderRelation;
import fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder.processor.QueryBuilderChainProcessor;
import fr.adrienbrault.idea.symfony2plugin.doctrine.querybuilder.util.MatcherUtil;
import fr.adrienbrault.idea.symfony2plugin.util.MethodMatcher;
import fr.adrienbrault.idea.symfony2plugin.util.PhpElementsUtil;
import fr.adrienbrault.idea.symfony2plugin.util.PsiElementUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class QueryBuilderCompletionContributor extends CompletionContributor {

    private static MethodMatcher.CallToSignature[] JOINS = new MethodMatcher.CallToSignature[] {
        new MethodMatcher.CallToSignature("\\Doctrine\\ORM\\QueryBuilder", "join"),
        new MethodMatcher.CallToSignature("\\Doctrine\\ORM\\QueryBuilder", "leftJoin"),
        new MethodMatcher.CallToSignature("\\Doctrine\\ORM\\QueryBuilder", "rightJoin"),
        new MethodMatcher.CallToSignature("\\Doctrine\\ORM\\QueryBuilder", "innerJoin"),
    };

    private static MethodMatcher.CallToSignature[] WHERES = new MethodMatcher.CallToSignature[] {
        new MethodMatcher.CallToSignature("\\Doctrine\\ORM\\QueryBuilder", "where"),
        new MethodMatcher.CallToSignature("\\Doctrine\\ORM\\QueryBuilder", "andWhere"),
        new MethodMatcher.CallToSignature("\\Doctrine\\ORM\\QueryBuilder", "orWhere"),
    };

    public QueryBuilderCompletionContributor() {

        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if (!Symfony2ProjectComponent.isEnabled(psiElement) || !(psiElement.getContext() instanceof StringLiteralExpression)) {
                    return;
                }

                MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.StringParameterMatcher(psiElement.getContext(), 0)
                    .withSignature("\\Doctrine\\ORM\\QueryBuilder", "setParameter")
                    .match();

                if(methodMatchParameter == null) {
                    methodMatchParameter = new MethodMatcher.ArrayParameterMatcher(psiElement.getContext(), 0)
                        .withSignature("\\Doctrine\\ORM\\QueryBuilder", "setParameters")
                        .match();
                }

                if(methodMatchParameter == null) {
                    return;
                }

                QueryBuilderMethodReferenceParser qb = getQueryBuilderParser(methodMatchParameter.getMethodReference());
                if(qb == null) {
                    return;
                }

                for(String parameter: qb.collect().getParameters()) {
                    completionResultSet.addElement(LookupElementBuilder.create(parameter));
                }

            }

        });

        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if (!Symfony2ProjectComponent.isEnabled(psiElement) || !(psiElement.getContext() instanceof StringLiteralExpression)) {
                    return;
                }

                MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.StringParameterMatcher(psiElement.getContext(), 0)
                    .withSignature(JOINS)
                    .match();

                if(methodMatchParameter == null) {
                    return;
                }

                QueryBuilderMethodReferenceParser qb = getQueryBuilderParser(methodMatchParameter.getMethodReference());
                if(qb == null) {
                    return;
                }

                QueryBuilderScopeContext collect = qb.collect();
                for(Map.Entry<String, List<QueryBuilderRelation>> parameter: collect.getRelationMap().entrySet()) {
                    for(QueryBuilderRelation relation: parameter.getValue()) {
                        completionResultSet.addElement(LookupElementBuilder.create(parameter.getKey() + "." + relation.getFieldName()).withTypeText(relation.getTargetEntity(), true));
                    }
                }

            }

        });

        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if(psiElement == null) {
                    return;
                }

                MethodMatcher.MethodMatchParameter methodMatchParameter = MatcherUtil.matchPropertyField(psiElement.getContext());
                if(methodMatchParameter == null) {
                    return;
                }

                QueryBuilderMethodReferenceParser qb = getQueryBuilderParser(methodMatchParameter.getMethodReference());
                if(qb == null) {
                    return;
                }

                QueryBuilderScopeContext collect = qb.collect();
                buildLookupElements(completionResultSet, collect);

            }

        });

        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if(psiElement == null) {
                    return;
                }

                // querybuilder parser is too slow longer values, and that dont make sense here at all
                // user can fire a manual completion event, when needed...
                if(completionParameters.isAutoPopup()) {
                    if(psiElement instanceof StringLiteralExpression) {
                        if(((StringLiteralExpression) psiElement).getContents().length() > 5) {
                            return;
                        }
                    }
                }

                MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.StringParameterMatcher(psiElement.getContext(), 0)
                    .withSignature(WHERES)
                    .match();

                if(methodMatchParameter == null) {
                    return;
                }

                QueryBuilderMethodReferenceParser qb = getQueryBuilderParser(methodMatchParameter.getMethodReference());
                if(qb == null) {
                    return;
                }

                QueryBuilderScopeContext collect = qb.collect();
                buildLookupElements(completionResultSet, collect);

            }

        });

        // $qb->join('test.foo', 'foo');
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if (!Symfony2ProjectComponent.isEnabled(psiElement) || !(psiElement.getContext() instanceof StringLiteralExpression)) {
                    return;
                }

                MethodMatcher.MethodMatchParameter methodMatchParameter = new MethodMatcher.StringParameterMatcher(psiElement.getContext(), 1)
                    .withSignature(JOINS)
                    .match();

                if(methodMatchParameter != null) {
                    MethodReference methodReference = PsiTreeUtil.getParentOfType(psiElement, MethodReference.class);
                    if(methodReference != null) {
                        String joinTable = PhpElementsUtil.getStringValue(PsiElementUtils.getMethodParameterPsiElementAt(methodReference, 0));
                        if(joinTable != null && StringUtils.isNotBlank(joinTable)) {
                            int pos = joinTable.lastIndexOf(".");
                            if(pos > 0) {
                                final String aliasName = joinTable.substring(pos + 1);
                                if(StringUtils.isNotBlank(aliasName)) {

                                    Set<String> strings = new HashSet<String>() {{
                                        add(aliasName);
                                        add(fr.adrienbrault.idea.symfony2plugin.util.StringUtils.camelize(aliasName, true));
                                        add(fr.adrienbrault.idea.symfony2plugin.util.StringUtils.underscore(aliasName));
                                    }};

                                    for(String string: strings) {
                                        completionResultSet.addElement(LookupElementBuilder.create(string));
                                    }

                                }
                            }
                        }
                    }
                }

            }

        });

    }

    private void buildLookupElements(CompletionResultSet completionResultSet, QueryBuilderScopeContext collect) {
        for(Map.Entry<String, QueryBuilderPropertyAlias> entry: collect.getPropertyAliasMap().entrySet()) {
            DoctrineModelField field = entry.getValue().getField();
            LookupElementBuilder lookup = LookupElementBuilder.create(entry.getKey());
            lookup = lookup.withIcon(Symfony2Icons.DOCTRINE);
            if(field != null) {
                lookup = lookup.withTypeText(field.getTypeName(), true);

                if(field.getRelationType() != null) {
                    lookup = lookup.withTailText("(" + field.getRelationType() + ")", true);
                    lookup = lookup.withTypeText(field.getRelation(), true);
                    lookup = lookup.withIcon(PhpIcons.CLASS_ICON);
                } else {
                    // relation tail text wins
                    String column = field.getColumn();
                    if(column != null) {
                        lookup = lookup.withTailText("(" + column + ")", true);
                    }
                }

            }

            // highlight fields which are possible in select statement
            if(collect.getSelects().contains(entry.getValue().getAlias())) {
                lookup = lookup.withBoldness(true);
            }

            completionResultSet.addElement(lookup);

        }
    }

    @Nullable
    public static QueryBuilderMethodReferenceParser getQueryBuilderParser(MethodReference methodReference) {
        final QueryBuilderChainProcessor processor = new QueryBuilderChainProcessor(methodReference);
        processor.collectMethods();

        // @TODO: pipe factory method
        return new QueryBuilderMethodReferenceParser(methodReference.getProject(), new ArrayList<MethodReference>() {{
            addAll(processor.getQueryBuilderFactoryMethods());
            addAll(processor.getQueryBuilderMethodReferences());
        }});

    }

}

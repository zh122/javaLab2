/**
 * Copyright 2016 Yurii Rashkovskii
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */
package graphql.annotations;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.annotations.annotationTypes.GraphQLDirectives;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.directives.AnnotationsDirectiveWiring;
import graphql.annotations.directives.AnnotationsWiringEnvironment;
import graphql.annotations.directives.Directive;
import graphql.annotations.directives.creation.DirectiveLocations;
import graphql.annotations.processor.GraphQLAnnotations;
import graphql.annotations.processor.exceptions.GraphQLAnnotationsException;
import graphql.annotations.processor.util.CodeRegistryUtil;
import graphql.introspection.Introspection;
import graphql.schema.*;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static graphql.Scalars.GraphQLBoolean;
import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLDirective.newDirective;
import static graphql.schema.GraphQLSchema.newSchema;
import static org.testng.Assert.*;

public class GraphQLDirectivesTest {

    private GraphQLAnnotations graphQLAnnotations;

    @BeforeMethod
    public void setUp() {
        this.graphQLAnnotations = new GraphQLAnnotations();
    }

    public static class UpperWiring implements AnnotationsDirectiveWiring {
        @Override
        public GraphQLFieldDefinition onField(AnnotationsWiringEnvironment environment) {
            GraphQLFieldDefinition field = (GraphQLFieldDefinition) environment.getElement();
            boolean isActive = (boolean) environment.getDirective().getArgument("isActive").getValue();
            CodeRegistryUtil.wrapDataFetcher(field, environment, (((dataFetchingEnvironment, value) -> {
                if (value instanceof String && isActive) {
                    return ((String) value).toUpperCase();
                }
                return value;
            })));

            return field;
        }
    }

    public static class SuffixWiring implements AnnotationsDirectiveWiring {
        @Override
        public GraphQLInputObjectField onInputObjectField(AnnotationsWiringEnvironment environment) {
            GraphQLInputObjectField field = (GraphQLInputObjectField) environment.getElement();
            String suffix = (String) environment.getDirective().getArgument("suffix").getValue();
            return field.transform(builder -> builder.name(field.getName() + suffix));
        }

        @Override
        public GraphQLFieldDefinition onField(AnnotationsWiringEnvironment environment) {
            GraphQLFieldDefinition field = (GraphQLFieldDefinition) environment.getElement();
            String suffix = (String) environment.getDirective().getArgument("suffix").getValue();
            CodeRegistryUtil.wrapDataFetcher(field, environment, (dataFetchingEnvironment, value) -> {
                if (value instanceof String) {
                    return value + suffix;
                }
                return value;
            });

            return field;
        }

        @Override
        public GraphQLArgument onArgument(AnnotationsWiringEnvironment environment) {
            GraphQLArgument element = (GraphQLArgument) environment.getElement();
            String suffix = (String) environment.getDirective().getArgument("suffix").getValue();
            return element.transform(builder -> builder.name(element.getName() + suffix));
        }

        @Override
        public GraphQLInputObjectType onInputObjectType(AnnotationsWiringEnvironment environment) {
            GraphQLInputObjectType element = (GraphQLInputObjectType) environment.getElement();
            String suffix = (String) environment.getDirective().getArgument("suffix").getValue();
            return element;
        }
    }

    public static class Query {
        @GraphQLField
        @GraphQLDirectives(@Directive(name = "upperCase", wiringClass = UpperWiring.class, argumentsValues = {"true"}))
        public static String name() {
            return "yarin";
        }

        @GraphQLField
        @GraphQLDirectives(@Directive(name = "upperCase", wiringClass = UpperWiring.class, argumentsValues = {"false"}))
        public static String nameWithFalse() {
            return "yarin";
        }
    }

    public static class Query2 {
        @GraphQLField
        @GraphQLDirectives(@Directive(name = "upperCase", wiringClass = UpperWiring.class))
        public static String nameWithNoArgs() {
            return "yarin";
        }
    }

    public static class Query3 {
        @GraphQLField
        @GraphQLDirectives({@Directive(name = "upperCase", wiringClass = UpperWiring.class, argumentsValues = {"true"}),
                @Directive(name = "suffix", wiringClass = SuffixWiring.class, argumentsValues = {"coolSuffix"})})
        public static String name() {
            return "yarin";
        }
    }

    public static class Query4 {
        @GraphQLField
        public static String nameWithArgument(@GraphQLDirectives({@Directive(name = "suffix",
                wiringClass = SuffixWiring.class, argumentsValues = {"coolSuffixForArg"})})
                                              @GraphQLName("extensionArg") String extensionArg) {
            return "yarin" + extensionArg;
        }
    }

    public static class InputObject {
        @GraphQLField
        @GraphQLDirectives({@Directive(name = "suffix", wiringClass = SuffixWiring.class, argumentsValues = {"coolSuffix"})})
        private String a;

        @GraphQLField
        private int b;

        public InputObject(@GraphQLName("a") String a, @GraphQLName("b") int b) {
            this.a = a;
            this.b = b;
        }
    }

    public static class Query5 {
        @GraphQLField
        public static String nameWithInputObject(@GraphQLName("inputObject") InputObject input) {
            return "yarin";
        }
    }

    @Test
    public void queryNameWithInputObject_directivesProvidedToRegistry_wiringOfInputObjectIsActivated() {
        GraphQLDirective suffixDirective = GraphQLDirective.newDirective().name("suffix").argument(builder -> builder.name("suffix").type(GraphQLString))
                .validLocations(Introspection.DirectiveLocation.INPUT_FIELD_DEFINITION, Introspection.DirectiveLocation.FIELD_DEFINITION).build();

        this.graphQLAnnotations.getContainer().getDirectiveRegistry().put(suffixDirective.getName(), suffixDirective);
        GraphQLObjectType object = this.graphQLAnnotations.object(Query5.class);
        GraphQLCodeRegistry codeRegistry = graphQLAnnotations.getContainer().getCodeRegistryBuilder().build();
        GraphQLSchema schema = newSchema().query(object).codeRegistry(codeRegistry).build();
        GraphQLFieldDefinition nameWithInputObject = schema.getQueryType().getFieldDefinition("nameWithInputObject");
        GraphQLInputObjectField field = ((GraphQLInputObjectType) nameWithInputObject.getArgument("inputObject").getType()).getField("acoolSuffix");
        assertNotNull(field);
    }

    @Test
    public void queryNameWithArgument_directivesProvidedToRegistry_wiringOfArgumentIsActivated() {
        GraphQLDirective suffixDirective = GraphQLDirective.newDirective().name("suffix").argument(builder -> builder.name("suffix").type(GraphQLString))
                .validLocations(Introspection.DirectiveLocation.FIELD_DEFINITION, Introspection.DirectiveLocation.ARGUMENT_DEFINITION).build();
        this.graphQLAnnotations.getContainer().getDirectiveRegistry().put(suffixDirective.getName(), suffixDirective);
        GraphQLObjectType object = this.graphQLAnnotations.object(Query4.class);
        GraphQLCodeRegistry codeRegistry = graphQLAnnotations.getContainer().getCodeRegistryBuilder().build();
        GraphQLSchema schema = newSchema().query(object).codeRegistry(codeRegistry).build();

        ExecutionResult result = GraphQL.newGraphQL(schema).build().execute("query { nameWithArgument(extensionArgcoolSuffixForArg: \"ext\") }");
        assertTrue(result.getErrors().isEmpty());
    }

    @Test(expectedExceptions = GraphQLAnnotationsException.class)
    public void queryName_noDirectivesProvidedToRegistry_exceptionIsThrown() throws Exception {
        GraphQLObjectType object = this.graphQLAnnotations.object(Query.class);
        GraphQLCodeRegistry codeRegistry = graphQLAnnotations.getContainer().getCodeRegistryBuilder().build();
        GraphQLSchema schema = newSchema().query(object).codeRegistry(codeRegistry).build();

        GraphQL.newGraphQL(schema).build().execute("query { name }");
    }

    @GraphQLName("upperCase")
    @DirectiveLocations(Introspection.DirectiveLocation.FIELD_DEFINITION)
    public static class UpperCase {
        boolean isActive;
    }

    @Test
    public void queryName_directivesProvidedToRegistry_wiringIsActivated() throws Exception {
        this.graphQLAnnotations.directive(UpperCase.class);
        GraphQLObjectType object = this.graphQLAnnotations.object(Query.class);
        GraphQLCodeRegistry codeRegistry = graphQLAnnotations.getContainer().getCodeRegistryBuilder().build();

        GraphQLSchema schema = newSchema().query(object).codeRegistry(codeRegistry).build();

        ExecutionResult result = GraphQL.newGraphQL(schema).build().execute("query { name }");
        assertTrue(result.getErrors().isEmpty());
        assertEquals(((Map<String, String>) result.getData()).get("name").toString(), "YARIN");
    }

    @Test
    public void queryNameWithFalse_directivesProvidedToRegistry_wiringIsActivated() throws Exception {
        GraphQLDirective upperCase = newDirective().name("upperCase").argument(builder -> builder.name("isActive").type(GraphQLBoolean))
                .validLocations(Introspection.DirectiveLocation.FIELD_DEFINITION).build();
        this.graphQLAnnotations.getContainer().getDirectiveRegistry().put(upperCase.getName(), upperCase);
        GraphQLObjectType object = this.graphQLAnnotations.object(Query.class);
        GraphQLCodeRegistry codeRegistry = graphQLAnnotations.getContainer().getCodeRegistryBuilder().build();
        GraphQLSchema schema = newSchema().query(object).codeRegistry(codeRegistry).build();

        ExecutionResult result = GraphQL.newGraphQL(schema).build().execute("query { nameWithFalse }");
        assertTrue(result.getErrors().isEmpty());
        assertEquals(((Map<String, String>) result.getData()).get("nameWithFalse").toString(), "yarin");
    }

    @Test
    public void queryNameWithNoArgs_directivesProvidedToRegistry_wiringIsActivated() throws Exception {
        GraphQLDirective upperCase = newDirective().name("upperCase").argument(builder -> builder.name("isActive").type(GraphQLBoolean).defaultValue(true))
                .validLocations(Introspection.DirectiveLocation.FIELD_DEFINITION).build();
        this.graphQLAnnotations.getContainer().getDirectiveRegistry().put(upperCase.getName(), upperCase);
        GraphQLObjectType object = this.graphQLAnnotations.object(Query2.class);
        GraphQLCodeRegistry codeRegistry = graphQLAnnotations.getContainer().getCodeRegistryBuilder().build();
        GraphQLSchema schema = newSchema().query(object).codeRegistry(codeRegistry).build();

        ExecutionResult result = GraphQL.newGraphQL(schema).build().execute("query { nameWithNoArgs }");
        assertTrue(result.getErrors().isEmpty());
        assertEquals(((Map<String, String>) result.getData()).get("nameWithNoArgs").toString(), "YARIN");
    }

    @Test(expectedExceptions = GraphQLAnnotationsException.class)
    public void queryNameWithNoArgs_noDefaultValue_exceptionIsThrown() throws Exception {
        GraphQLDirective upperCase = newDirective().name("upperCase").argument(builder -> builder.name("isActive").type(GraphQLBoolean))
                .validLocations(Introspection.DirectiveLocation.FIELD_DEFINITION).build();
        this.graphQLAnnotations.getContainer().getDirectiveRegistry().put(upperCase.getName(), upperCase);
        GraphQLObjectType object = this.graphQLAnnotations.object(Query2.class);
        GraphQLCodeRegistry codeRegistry = graphQLAnnotations.getContainer().getCodeRegistryBuilder().build();

        GraphQLSchema schema = newSchema().query(object).codeRegistry(codeRegistry).build();

        GraphQL.newGraphQL(schema).build().execute("query { nameWithNoArgs }");
    }

    @Test
    public void queryName_chainedDirectives_wiringIsActivatedInCorrectOrder() throws Exception {
        GraphQLDirective upperCase = newDirective().name("upperCase").argument(builder -> builder.name("isActive").type(GraphQLBoolean).defaultValue(true))
                .validLocations(Introspection.DirectiveLocation.FIELD_DEFINITION).build();
        GraphQLDirective suffixDirective = GraphQLDirective.newDirective().name("suffix").argument(builder -> builder.name("suffix").type(GraphQLString))
                .validLocations(Introspection.DirectiveLocation.FIELD_DEFINITION, Introspection.DirectiveLocation.ARGUMENT_DEFINITION).build();
        this.graphQLAnnotations.getContainer().getDirectiveRegistry().put(upperCase.getName(), upperCase);
        this.graphQLAnnotations.getContainer().getDirectiveRegistry().put(suffixDirective.getName(), suffixDirective);
        GraphQLObjectType object = this.graphQLAnnotations.object(Query3.class);
        GraphQLCodeRegistry codeRegistry = graphQLAnnotations.getContainer().getCodeRegistryBuilder().build();

        GraphQLSchema schema = newSchema().query(object).codeRegistry(codeRegistry).build();

        ExecutionResult result = GraphQL.newGraphQL(schema).build().execute("query { name }");
        assertTrue(result.getErrors().isEmpty());
        assertEquals(((Map<String, String>) result.getData()).get("name").toString(), "YARINcoolSuffix");
    }


}

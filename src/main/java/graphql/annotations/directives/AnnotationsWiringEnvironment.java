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
package graphql.annotations.directives;

import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;

public interface AnnotationsWiringEnvironment {
    /**
     * @return the runtime element in play
     */
    GraphQLDirectiveContainer getElement();

    /**
     * @return the directive that is being examined
     */
    GraphQLDirective getDirective();

    /**
     *
     * @return the parent name of the element
     */
    String getParentName();


    /**
     *
     * @return the code registry builder
     */
    GraphQLCodeRegistry.Builder getCodeRegistryBuilder();
}

/*
 * Copyright (C) 2014-2018 D3X Systems - All Rights Reserved
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
package com.d3x.morpheus.frame;

import java.util.function.Consumer;

/**
 * A base class for building DataFrameSource implementations that define a unified interface for loading DataFrames from various data sources.
 *
 * @param <R>   the row key type
 * @param <C>   the column key type
 *
 * <p>This is open source software released under the <a href="http://www.ap`ache.org/licenses/LICENSE-2.0">Apache 2.0 License</a></p>
 *
 * @author  Xavier Witdouck
 */
public interface DataFrameSource<R,C,O> {

    /**
     * Returns a <code>DataFrame</code> read from some underlying device based on options
     * @param configurator  the options consumer to configure load options
     * @return              the <code>DataFrame</code> response
     * @throws DataFrameException  if this operation fails
     */
    DataFrame<R,C> read(Consumer<O> configurator) throws DataFrameException;


    /**
     * Applies the options to the configurator and then validates
     * @param options       the empty options instance
     * @param configurator  the configurator to configure instance
     * @return              the configured options
     */
    default <T> T initOptions(T options, Consumer<T> configurator) {
        configurator.accept(options);
        return options;
    }

}

/*
 * Copyright 2026 Axel Howind - axh@dua3.com
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
 * limitations under the License.
 */

import org.jspecify.annotations.NullMarked;
import org.slb4j.frontend.jcl.LogFactoryJcl;
import org.slb4j.frontend.log4j.ProviderLog4j;
import org.slb4j.frontend.slf4j.LoggingServiceProviderSlf4j;

/**
 * Module-info for the SLB4J logging backend library.
 */
@NullMarked
module org.slb4j {
    exports org.slb4j;
    exports org.slb4j.filter;
    exports org.slb4j.handler;
    exports org.slb4j.layout;
    exports org.slb4j.support;

    exports org.slb4j.frontend.log4j;
    exports org.slb4j.frontend.slf4j;
    exports org.slb4j.frontend.jcl;

    opens org.slb4j.frontend.log4j;
    opens org.slb4j.frontend.slf4j;
    opens org.slb4j.frontend.jcl;
    exports org.slb4j.support.formatter;
    exports org.slb4j.config;

    requires org.jspecify;

    requires static java.logging;
    requires static org.apache.commons.logging;
    requires static org.apache.logging.log4j;
    requires static org.slf4j;

    provides org.slf4j.spi.SLF4JServiceProvider with LoggingServiceProviderSlf4j;
    provides org.apache.logging.log4j.spi.Provider with ProviderLog4j;
    provides org.apache.commons.logging.LogFactory with LogFactoryJcl;
}

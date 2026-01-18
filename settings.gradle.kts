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
rootProject.name = "slb4j"

include("benchmark")
include("samples:jul")
include("samples:jcl")
include("samples:log4j")
include("samples:slf4j")
include("samples:all")
include("slb4j-ext")
include("slb4j-ext:slb4j-ext-fx")
include("slb4j-ext:slb4j-ext-fx:samples")
include("slb4j-ext:slb4j-ext-swing")
include("slb4j-ext:slb4j-ext-swing:samples")

include("benchmark:benchmark-jul")
include("benchmark:benchmark-logback")
include("benchmark:benchmark-log4j")
include("benchmark:benchmark-slb4j")
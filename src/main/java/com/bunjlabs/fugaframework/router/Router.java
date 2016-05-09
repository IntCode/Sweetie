/* 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bunjlabs.fugaframework.router;

import com.bunjlabs.fugaframework.FugaApp;
import com.bunjlabs.fugaframework.foundation.Context;
import com.bunjlabs.fugaframework.foundation.Controller;
import com.bunjlabs.fugaframework.foundation.Response;
import com.bunjlabs.fugaframework.foundation.ResponseGiver;
import com.bunjlabs.fugaframework.foundation.Responses;
import com.bunjlabs.fugaframework.resources.ResourceManager;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Router {

    private static final Logger log = LogManager.getLogger(Router.class);

    private final ResourceManager resourceManager;
    private final List<Extension> extensions = new ArrayList<>();

    private final Map<Integer, ResponseGiver> defaultErrorResponses = new HashMap<>();

    public Router(FugaApp app) {
        this.resourceManager = app.getResourceManager();
    }

    public void load(String path) {
        try {
            load(resourceManager.load(path));
            log.info("Routes loaded from: {}", path);
        } catch (NullPointerException | RoutesMapLoadException | RoutesMapSyntaxException ex) {
            log.error("Unable to load routes from: {}", path, ex);
        }
    }

    public void loadFromResources(String path) {
        try {
            load(resourceManager.loadFromResources(path));
            log.info("Routes loaded from resources: {}", path);
        } catch (NullPointerException | RoutesMapLoadException | RoutesMapSyntaxException ex) {
            log.error("Unable to load routes from: {}", path, ex);
        }
    }

    public void loadFromString(String input) throws NullPointerException, RoutesMapLoadException, RoutesMapSyntaxException {
        load(new ByteArrayInputStream(input.getBytes()));
    }

    private void load(InputStream input) throws NullPointerException, RoutesMapLoadException, RoutesMapSyntaxException {
        if (input == null) {
            throw new NullPointerException();
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(input));

        RouteMapLoader mapLoader = new RouteMapLoader();

        extensions.addAll(mapLoader.load(input));
    }

    public void setDefaultErrorResponse(int status, ResponseGiver rg) {
        defaultErrorResponses.put(status, rg);
    }

    public Response forward(FugaApp app, Context ctx) {
        if (extensions.isEmpty()) {
            Exception ex = new RoutesMapException("Empty routes map");
            log.catching(ex);
            return Responses.internalServerError(ex);
        }

        Response resp;

        try {
            resp = forward(app, ctx, extensions);
        } catch (Exception ex) {
            log.catching(ex);
            return Responses.internalServerError(ex);
        }

        if (resp == null) {
            resp = Responses.notFound();
        }

        if (resp.isEmpty()) {
            resp = defaultErrorResponses.get(resp.getStatus()).get();
        }

        if (resp.getStatus() == 404 && resp.getStream() == null) {
            resp = Responses.notFound("404 Not Found"); //TODO: Remove kostyl
        }

        return resp;
    }

    private Response forward(FugaApp app, Context ctx, List<Extension> exts) throws Exception {
        if (exts == null) {
            throw new RoutesMapException("Extensions sublist is null");
        }
        Matcher m;
        for (Extension ext : exts) {
            if (ext.getRequestMethods() != null && !ext.getRequestMethods().isEmpty() && !ext.getRequestMethods().contains(ctx.getRequest().getRequestMethod())) {
                continue;
            }
            if (ext.getPattern() == null) {
                m = null;
            } else if (!(m = ext.getPattern().matcher(ctx.getRequest().getPath())).matches()) {
                continue;
            }

            if (ext.getRoute() != null) {
                Route route = ext.getRoute();
                Object[] args = new Object[route.getParameters().size()];

                for (int i = 0; i < args.length; i++) {
                    RouteParameter mp = route.getParameters().get(i);
                    if (mp.getType() == RouteParameter.ParameterType.CAPTURE_GROUP) {
                        args[i] = mp.cast(m.group(mp.getCaptureGroup()));
                    } else {
                        args[i] = mp.cast();
                    }
                }
                Response resp = invoke(app, ctx, route, args);
                if (resp != null) {
                    return resp;
                }
            } else if (ext.getNodes() != null && !ext.getNodes().isEmpty()) {
                Response resp = (Response) forward(app, ctx, ext.getNodes());
                if (resp != null) {
                    return resp;
                }
            }
        }
        return null;
    }

    private Response invoke(FugaApp app, Context ctx, Route route, Object... args) throws Exception {
        Controller controller = Controller.Builder.build(route.getController(), app, ctx);
        return (Response) route.getMethod().invoke(controller, args);
    }
}

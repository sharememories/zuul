/**
 * Copyright 2014 Netflix, Inc.
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
package com.netflix.zuul;

import rx.Observable;

import java.util.List;

public class FilterProcessor {

    private final FilterStore filterStore;

    public FilterProcessor(FilterStore filterStore) {
        this.filterStore = filterStore;
    }

    public Observable<EgressResponse> applyAllFilters(IngressRequest ingressReq, EgressResponse egressResp) {
        FiltersForRoute filtersForRoute = filterStore.getFilters(ingressReq);
        return applyPreFilters(ingressReq, filtersForRoute.getPreFilters()).flatMap(egressReq ->
                applyRoutingFilter(egressReq, filtersForRoute.getRouteFilter())).flatMap(ingressResp ->
                applyPostFilters(ingressResp, egressResp, filtersForRoute.getPostFilters()));
    }

    private Observable<EgressRequest> applyPreFilters(IngressRequest ingressReq, List<PreFilter> preFilters) {
        System.out.println("IngressReq : " + ingressReq + ", preFilters : " + preFilters.size());

        return Observable.from(preFilters).reduce(Observable.just(EgressRequest.copiedFrom(ingressReq)), (egressReqObservable, preFilter) -> {
            return preFilter.shouldFilter(ingressReq).flatMap(shouldFilter -> {
                if (shouldFilter) {
                    return egressReqObservable.flatMap(egressReq -> preFilter.apply(ingressReq, egressReq));
                } else {
                    return egressReqObservable;
                }
            });
        }).flatMap(o -> o);
    }

    private Observable<IngressResponse> applyRoutingFilter(EgressRequest egressReq, RouteFilter routeFilter) {
        System.out.println("EgressReq : " + egressReq);
        if (routeFilter == null) {
            return Observable.error(new ZuulException("You must define a RouteFilter."));
        } else {
            return routeFilter.apply(egressReq);
        }
    }

    private Observable<EgressResponse> applyPostFilters(IngressResponse ingressResp, EgressResponse initialEgressResp, List<PostFilter> postFilters) {
        System.out.println("IngressResp : " + ingressResp + ", postFilters : " + postFilters.size());

        Observable<EgressResponse> initialEgressRespObservable = Observable.just(initialEgressResp.copyFrom(ingressResp));
        return Observable.from(postFilters).reduce(initialEgressRespObservable, (egressRespObservable, postFilter) ->
                postFilter.shouldFilter(ingressResp).flatMap(shouldFilter -> {
                    if (shouldFilter) {
                        return egressRespObservable.flatMap(egressResp -> postFilter.apply(ingressResp, egressResp));
                    } else {
                        return egressRespObservable;
                    }
                })).flatMap(o -> o);
    }
}

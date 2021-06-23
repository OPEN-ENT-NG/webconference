import {
    appPrefix,
    model,
    ng,
    Shareable,
    ShareAction,
    ShareInfos,
    ShareVisible,
    http, Model, idiom, _, Me
} from "entcore";

export interface ShareableWithId extends Shareable {
    _id: string
}
export interface SharePanelScope {
    display: {
        workflowAllowSharebookmarks: boolean,
        search: {
            processing: Boolean
        }
    }
    sharingModel: ShareInfos & { edited: any[], changed?: boolean, sharebookmarks?: any }
    resources: ShareableWithId[] | ShareableWithId
    maxResults: number
    translate: any
    actions: ShareAction[]
    search: string
    found: ShareVisible[]
    shareOverrideDefaultActions: string[]
    findUserOrGroup()
    typeSort(sort: any)
    addResults()
    addEdit(item)
    remove(el: ShareVisible)
    clearSearch()
    onChange(args: { data: any[] })
    //angular
    $apply(a?)
    $watch(a?, b?)
    $watchCollection(a?, b?)
}
export const searchInvitees = ng.directive('searchInvitees', ['$rootScope', ($rootScope) => {
    return {
        scope: {
            resources: '=',
            onChange: '&'
        },
        restrict: 'E',
        template: `
            <div class="share temp row">
                <!-- Title -->
                <div class="flex-row align-center">
                    <div class= "size-auto" style="font-weight: bold;">
                        <span ng-if="!display.workflowAllowSharebookmarks || display.workflowAllowSharebookmarks == false">[[translate('share.search.title')]]</span>
                        <span workflow="directory.allowSharebookmarks">[[translate('share.search.sharebookmark.title')]]</span>
                    </div>
                </div>
                
                <!-- Search bar -->
                <search-user class="twelve cell bottom-spacing-three" 
                    ng-model="search"
                    clear-list="clearSearch()"
                    on-send="findUserOrGroup()"
                    search-track="display.search">
                </search-user>
                <div class="found-users-list">
                    <div ng-repeat="item in found | orderBy:[typeSort, 'name', 'username'] | limitTo:maxResults" class="row autocomplete temp">
                        <div ng-click="addEdit(item)" class="row">
                            <a class="cell right-spacing" ng-class="{sharebookmark: item.type === 'sharebookmark'}">
                                <i class="add-favorite cell" ng-if="item.type === 'sharebookmark'"></i>
                                <!-- group or sharebookmark name -->
                                <span ng-if="item.name">[[item.name]]</span>
                                <!-- user displayName -->
                                <span ng-if="item.username">[[item.username]]</span>
                            </a>
                            <!-- structureName for groups -->
                            <em ng-if="item.structureName" class="low-importance">[[item.structureName]]</em>
                            <!-- profile for users -->
                            <em ng-if="item.profile" class="low-importance">[[translate(item.profile)]]</em>
                        </div>
                    </div>
                    <div class="row" ng-if="found.length === 0 && !display.search.processing">
                        <label class="low-importance medium-importance italic-text" ng-if="search.length >= 3" user-role="ADMIN_LOCAL">
                            <i18n>portal.no.result</i18n>
                        </label>
                        <label class="low-importance medium-importance italic-text" ng-if="search.length" user-missing-role="ADMIN_LOCAL">
                            <i18n>portal.no.result</i18n>
                        </label>
                    </div>
                    <div class="row">
                        <a ng-click="addResults()" ng-if="found.length > maxResults" class="right-magnet reduce-block-four">
                            <i18n>seemore</i18n>
                        </a>
                    </div>
                </div>
                
                <!-- Results -->
                <div class="top-spacing-three bottom-spacing-twice" style="display: flex; align-items: baseline;">
                    <i18n class="two" style="font-weight: bold">webconference.invitation.cc.label</i18n>
                    <div class="ten" style="display: flex; flex-wrap: wrap;">
                        <contact-chip ng-repeat="item in sharingModel.edited" ng-if="!item.hide || item.hide != true"
                                    ng-model="item" action="remove(item)"
                                    class="block relative removable">
                        </contact-chip>
                    </div>
                </div>
            </div>
        `,
        link: function ($scope: SharePanelScope) {
            let usersCache = {};
            $scope.translate = idiom.translate;
            $scope.found = [];
            $scope.maxResults = 5;
            $scope.sharingModel = {
                edited: [],
                ids: [],
                changed: false
            } as any;
            $scope.display = {
                workflowAllowSharebookmarks: false,
                search: {
                    processing: false
                }
            }

            if (!($scope.resources instanceof Array) && !$scope.resources.myRights && !($scope.resources instanceof Model)) {
                throw new TypeError('Resources in share panel must be instance of Array or implement Rights interface');
            }
            if (!($scope.resources instanceof Array)) { $scope.resources = [$scope.resources]; }

            // Get directory workflow to manage allowSharebookmarks workflow
            async function loadDirectoryWorkflow() {
                await model.me.workflow.load(['directory']);
                $scope.display.workflowAllowSharebookmarks = model.me.workflow.directory.allowSharebookmarks;
                $scope.$apply();
            }
            loadDirectoryWorkflow();

            $scope.findUserOrGroup = function () {
                let searchTerm = idiom.removeAccents($scope.search).toLowerCase();
                let startSearch = Me.session.functions.ADMIN_LOCAL ? searchTerm.substr(0, 3) : '';
                if (!usersCache[startSearch] && !(usersCache[startSearch] && usersCache[startSearch].loading)) {
                    usersCache[startSearch] = { loading: true };
                    let id = $scope.resources[0]._id;
                    let path = '/' + appPrefix + '/share/json/' + id + '?search=' + startSearch;
                    http().get(path).done(function (data) {
                        data.users.visibles.map(user => user.type = 'user');
                        data.groups.visibles.map(group => group.type = 'group');

                        _.forEach(data.users.visibles, function(user) { user.displayName = user.username });
                        _.forEach(data.groups.visibles, function(group) { group.displayName = group.name });

                        usersCache[startSearch] = { groups: data.groups, users: data.users };
                        $scope.sharingModel.groups = usersCache[startSearch].groups;
                        $scope.sharingModel.users = usersCache[startSearch].users;

                        if (model.me.workflow.directory.allowSharebookmarks == true) {
                            http().get('/directory/sharebookmark/all').done(function (data) {
                                let bookmarks = _.map(data, function (bookmark) {
                                    bookmark.type = 'sharebookmark';
                                    return bookmark;
                                });
                                usersCache[startSearch]['sharebookmarks'] = bookmarks;

                                $scope.findUserOrGroup();
                                $scope.$apply();
                            });
                        } else {
                            $scope.findUserOrGroup();
                            $scope.$apply();
                        }
                    });
                    return;
                }
                $scope.sharingModel.groups = usersCache[startSearch].groups;
                $scope.sharingModel.users = usersCache[startSearch].users;
                $scope.sharingModel.sharebookmarks = usersCache[startSearch].sharebookmarks;

                $scope.found = _.union(
                    _.filter($scope.sharingModel.sharebookmarks, function (bookmark) {
                        let testName = idiom.removeAccents(bookmark.name).toLowerCase();
                        return testName.indexOf(searchTerm) !== -1 && $scope.sharingModel.edited.find(i => i.id === bookmark.id) === undefined;
                    }),
                    _.filter($scope.sharingModel.groups == null ? [] : $scope.sharingModel.groups.visibles, function (group) {
                        let testName = idiom.removeAccents(group.name).toLowerCase();
                        return testName.indexOf(searchTerm) !== -1 && $scope.sharingModel.edited.find(i => i.id === group.id) === undefined;
                    }),
                    _.filter($scope.sharingModel.users == null ? [] : $scope.sharingModel.users.visibles, function (user) {
                        const testName = idiom.removeAccents(user.lastName + ' ' + user.firstName).toLowerCase();
                        let testNameReversed = idiom.removeAccents(user.firstName + ' ' + user.lastName).toLowerCase();
                        let testUsername = idiom.removeAccents(user.username).toLowerCase();
                        return (testName.indexOf(searchTerm) !== -1 || testNameReversed.indexOf(searchTerm) !== -1) || testUsername.indexOf(searchTerm) !== -1 && $scope.sharingModel.edited.find(i => i.id === user.id) === undefined;
                    })
                );
                $scope.found = _.filter($scope.found, function (element) {
                    return $scope.sharingModel.edited.findIndex(i => i.id === element.id) === -1;
                })

                $scope.display.search.processing = false;
                $scope.$apply();
            };

            $scope.typeSort = function (value) {
                if (value.type == 'sharebookmark') return 0;
                if (value.type == 'group') return 1;
                return 2;
            };

            $scope.addResults = function () { $scope.maxResults += 5; };

            $scope.addEdit = function (item) {
                $scope.sharingModel.edited.push(item);
                item.index = $scope.sharingModel.edited.length;

                let processBookmark = false;
                if (item.type == 'sharebookmark') {
                    processBookmark = true;
                    http().get('/directory/sharebookmark/' + item.id).done(function (data) {
                        item.users = data.users;
                        item.groups = data.groups;
                        $scope.onChange({'data': $scope.sharingModel.edited});
                        $scope.$apply();
                    });
                }

                $scope.sharingModel.changed = true;
                $scope.clearSearch();
                $scope.search = '';
                if (!processBookmark) $scope.onChange({'data': $scope.sharingModel.edited});
            };

            $scope.remove = function (element) {
                $scope.sharingModel.edited = _.reject($scope.sharingModel.edited, function (item) {
                    return item.id === element.id;
                });
                $scope.sharingModel.changed = true;
                $scope.onChange({'data': $scope.sharingModel.edited});
            };

            $scope.clearSearch = function () {
                $scope.sharingModel.groups = [] as any;
                $scope.sharingModel.users = [] as any;
                $scope.found = [];
            };

            $scope.$watch('resources', function () {
                $scope.actions = [];
                $scope.sharingModel.edited = [];
                $scope.search = '';
                $scope.found = [];
                $scope.sharingModel.changed = false;
                $scope.maxResults = 5;
            });

            $scope.$watchCollection('resources', function () {
                $scope.actions = [];
                $scope.sharingModel.edited = [];
                $scope.search = '';
                $scope.found = [];
                $scope.sharingModel.changed = false;
                $scope.maxResults = 5;
            });
        }
    }
}]);
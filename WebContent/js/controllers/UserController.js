'use strict';

angular.module('web')
    .controller('UserController', function ($scope, Users, Storage, $modal, ITEMS_BY_PAGE, DISPLAY_PAGES) {
        $scope.itemsByPage = ITEMS_BY_PAGE;
        $scope.displayedPages = DISPLAY_PAGES;
        $scope.users = [];

        var currentUser = Users.getCurrent();
        $scope.isAdmin = currentUser.role === "admin";
        $scope.isCurrentUser = function (email) {
            return currentUser.email === email;
        };

        var updateCurrentUser = function () {
            if($scope.isCurrentUser($scope.userToEdit.email)) {
                var user = angular.copy($scope.userToEdit);
                delete user.isNew;
                delete user.password;
                delete user.admin;
                user.role = $scope.userToEdit.admin ? 'admin' : 'user';
                Storage.save("currentUser", user);
            };
        };

        $scope.editUser = function (user) {
            $scope.userToEdit = angular.copy(user);
            $scope.userToEdit.isNew = false;
            var editUserModal = $modal.open({
                animation: true,
                templateUrl: './partials/modal.user-edit.html',
                scope: $scope
            });

            editUserModal.result.then(function () {
                $scope.isLoading = true;
                $scope.userToEdit.password = $scope.userToEdit.password || "";

                Users.update($scope.userToEdit).then(function () {
                    $scope.refreshUsers();
                    updateCurrentUser();
                    var confirmModal = $modal.open({
                        animation: true,
                        templateUrl: './partials/modal.user-added.html',
                        scope: $scope
                    });
                }, function (e) {
                    console.log(e);
                });
                $scope.isLoading = false;
            });
        };

        $scope.addUser = function () {
            $scope.userToEdit = {};
            $scope.userToEdit.isNew = true;
            $scope.userToEdit.admin = false;
            var modalInstance = $modal.open({
                animation: true,
                templateUrl: './partials/modal.user-edit.html',
                scope: $scope
            });

            modalInstance.result.then(function () {
                $scope.isLoading = true;

                Users.insert($scope.userToEdit).then(function () {
                    var modalInstance = $modal.open({
                        animation: true,
                        templateUrl: './partials/modal.user-added.html',
                        scope: $scope
                    }, function (e) {
                        console.log(e);
                    });

                    modalInstance.result.then(function () {
                        $scope.refreshUsers();
                    });
                    $scope.isLoading = false;
                });
                $scope.isLoading = false;
            });
        };

        Users.getAll().then(function (data) {
            $scope.users = data;
        });

        $scope.refreshUsers = function () {
            $scope.isLoading = true;
            $scope.users = [];
            Users.getAll().then(function (data) {
                $scope.users = data;
                $scope.isLoading = false;
            }, function () {
                $scope.isLoading = false;
            })
        };

        $scope.deleteUser = function (user) {
            $scope.userToDelete = user;
            var modalInstance = $modal.open({
                animation: true,
                templateUrl: './partials/modal.user-delete.html',
                scope: $scope
            });

            modalInstance.result.then(function () {
                $scope.isLoading = true;
                Users.delete(user.email).then(function () {
                    $scope.refreshUsers();
                    $scope.isLoading = false;
                }, function () {
                    $scope.isLoading = false;
                });
            })
        };
    });
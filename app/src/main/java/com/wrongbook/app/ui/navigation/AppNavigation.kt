package com.wrongbook.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.wrongbook.app.ui.screens.addedit.AddEditScreen
import com.wrongbook.app.ui.screens.detail.DetailScreen
import com.wrongbook.app.ui.screens.home.HomeScreen
import com.wrongbook.app.ui.screens.list.QuestionListScreen
import com.wrongbook.app.ui.screens.review.ReviewScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object QuestionList : Screen("question_list")
    object AddQuestion : Screen("add_question")
    object EditQuestion : Screen("edit_question/{questionId}") {
        fun createRoute(questionId: String) = "edit_question/$questionId"
    }
    object QuestionDetail : Screen("question_detail/{questionId}") {
        fun createRoute(questionId: String) = "question_detail/$questionId"
    }
    object Review : Screen("review")
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        composable(
            route = "question_list?category={category}",
            arguments = listOf(navArgument("category") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { entry ->
            val category = entry.arguments?.getString("category")
            QuestionListScreen(navController = navController, initialCategory = category)
        }

        composable(Screen.AddQuestion.route) {
            AddEditScreen(navController = navController)
        }

        composable(
            route = Screen.EditQuestion.route,
            arguments = listOf(navArgument("questionId") { type = NavType.StringType })
        ) { entry ->
            val questionId = entry.arguments?.getString("questionId") ?: return@composable
            AddEditScreen(navController = navController, questionId = questionId)
        }

        composable(
            route = Screen.QuestionDetail.route,
            arguments = listOf(navArgument("questionId") { type = NavType.StringType })
        ) { entry ->
            val questionId = entry.arguments?.getString("questionId") ?: return@composable
            DetailScreen(navController = navController, questionId = questionId)
        }

        composable(Screen.Review.route) {
            ReviewScreen(navController = navController)
        }
    }
}

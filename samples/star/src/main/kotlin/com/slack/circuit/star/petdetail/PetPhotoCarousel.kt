// Copyright (C) 2022 Slack Technologies, LLC
// SPDX-License-Identifier: Apache-2.0
package com.slack.circuit.star.petdetail

import android.view.KeyEvent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationConstants
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.google.accompanist.pager.HorizontalPagerIndicator
import com.slack.circuit.codegen.annotations.CircuitInject
import com.slack.circuit.overlay.LocalOverlayHost
import com.slack.circuit.runtime.CircuitUiState
import com.slack.circuit.runtime.Screen
import com.slack.circuit.runtime.internal.rememberStableCoroutineScope
import com.slack.circuit.runtime.presenter.Presenter
import com.slack.circuit.star.common.ImmutableListParceler
import com.slack.circuit.star.di.AppScope
import com.slack.circuit.star.imageviewer.ImageViewerScreen
import com.slack.circuit.star.petdetail.PetPhotoCarouselTestConstants.CAROUSEL_TAG
import com.slack.circuit.star.ui.LocalWindowWidthSizeClass
import com.slack.circuitx.overlays.showFullScreenOverlay
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlin.math.absoluteValue
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

/*
 * This is a trivial example of a photo carousel used in the pet detail screen. We'd normally likely
 * write this UI directly in the detail screen UI itself, but it's helpful for demonstrating
 * nested Circuit UIs in this sample app.
 *
 * This differs from some other screens by only displaying the input screen directly as static
 * state, as opposed to reading from a repository or maintaining any sort of produced state.
 */

@Parcelize
data class PetPhotoCarouselScreen(
  val name: String,
  @TypeParceler<ImmutableList<String>, ImmutableListParceler> val photoUrls: ImmutableList<String>,
  val photoUrlMemoryCacheKey: String?,
) : Screen {
  data class State(
    val name: String,
    val photoUrls: ImmutableList<String>,
    val photoUrlMemoryCacheKey: String?,
  ) : CircuitUiState {
    companion object {
      operator fun invoke(screen: PetPhotoCarouselScreen): State {
        return State(
          name = screen.name,
          photoUrls = screen.photoUrls.toImmutableList(),
          photoUrlMemoryCacheKey = screen.photoUrlMemoryCacheKey,
        )
      }
    }
  }
}

// TODO can we make a StaticStatePresenter for cases like this? Maybe even generate _from_ the
//  screen type?
class PetPhotoCarouselPresenter
@AssistedInject
constructor(@Assisted private val screen: PetPhotoCarouselScreen) :
  Presenter<PetPhotoCarouselScreen.State> {

  @Composable override fun present() = PetPhotoCarouselScreen.State(screen)

  @CircuitInject(PetPhotoCarouselScreen::class, AppScope::class)
  @AssistedFactory
  interface Factory {
    fun create(screen: PetPhotoCarouselScreen): PetPhotoCarouselPresenter
  }
}

internal object PetPhotoCarouselTestConstants {
  const val CAROUSEL_TAG = "carousel"
}

@Suppress("DEPRECATION") // https://github.com/google/accompanist/issues/1551
@OptIn(ExperimentalFoundationApi::class, com.google.accompanist.pager.ExperimentalPagerApi::class)
@CircuitInject(PetPhotoCarouselScreen::class, AppScope::class)
@Composable
internal fun PetPhotoCarousel(state: PetPhotoCarouselScreen.State, modifier: Modifier = Modifier) {
  val (name, photoUrls, photoUrlMemoryCacheKey) = state
  val context = LocalContext.current
  // Prefetch images
  LaunchedEffect(Unit) {
    for (url in photoUrls) {
      if (url.isBlank()) continue
      val request = ImageRequest.Builder(context).data(url).build()
      context.imageLoader.enqueue(request)
    }
  }

  val totalPhotos = photoUrls.size
  val pagerState = rememberPagerState { totalPhotos }
  val scope = rememberStableCoroutineScope()
  val requester = remember { FocusRequester() }
  @Suppress("MagicNumber")
  val columnModifier =
    when (LocalWindowWidthSizeClass.current) {
      WindowWidthSizeClass.Medium,
      WindowWidthSizeClass.Expanded -> modifier.fillMaxWidth(0.5f)
      else -> modifier.fillMaxSize()
    }
  Column(
    columnModifier
      .testTag(CAROUSEL_TAG)
      // Some images are different sizes. We probably want to constrain them to the same common
      // size though
      .animateContentSize()
      .focusRequester(requester)
      .focusable()
      .onKeyEvent { event ->
        if (event.nativeKeyEvent.action != KeyEvent.ACTION_UP) return@onKeyEvent false
        val index =
          when (event.nativeKeyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
              pagerState.currentPage.inc().takeUnless { it >= totalPhotos } ?: -1
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
              pagerState.currentPage.dec().takeUnless { it < 0 } ?: -1
            }
            else -> -1
          }
        if (index == -1) {
          false
        } else {
          scope.launch { pagerState.animateScrollToPage(index) }
          true
        }
      }
  ) {
    PhotoPager(
      pagerState = pagerState,
      photoUrls = photoUrls,
      name = name,
      photoUrlMemoryCacheKey = photoUrlMemoryCacheKey,
    )

    HorizontalPagerIndicator(
      pagerState = pagerState,
      pageCount = totalPhotos,
      modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp),
      activeColor = MaterialTheme.colorScheme.onBackground
    )
  }

  // Focus the pager so we can cycle through it with arrow keys
  LaunchedEffect(Unit) { requester.requestFocus() }
}

@OptIn(ExperimentalFoundationApi::class)
private fun PagerState.calculateCurrentOffsetForPage(page: Int): Float {
  return (currentPage - page) + currentPageOffsetFraction
}

@Suppress("LongParameterList")
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoPager(
  pagerState: PagerState,
  photoUrls: ImmutableList<String>,
  name: String,
  modifier: Modifier = Modifier,
  photoUrlMemoryCacheKey: String? = null,
) {
  HorizontalPager(
    state = pagerState,
    key = photoUrls::get,
    modifier = modifier,
    contentPadding = PaddingValues(16.dp),
  ) { page ->
    val overlayHost = LocalOverlayHost.current
    val photoUrl by remember { derivedStateOf { photoUrls[page].takeIf(String::isNotBlank) } }
    val scope = rememberStableCoroutineScope()

    val clickableModifier =
      photoUrl?.let { url ->
        Modifier.clickable {
          scope.launch {
            overlayHost.showFullScreenOverlay(
              ImageViewerScreen(id = url, url = url, placeholderKey = name)
            )
          }
        }
      }
        ?: Modifier
    Card(
      modifier =
        clickableModifier.aspectRatio(1f).graphicsLayer {
          // Calculate the absolute offset for the current page from the
          // scroll position. We use the absolute value which allows us to mirror
          // any effects for both directions
          val pageOffset = pagerState.calculateCurrentOffsetForPage(page).absoluteValue

          // We animate the scaleX + scaleY, between 85% and 100%
          lerp(start = 0.85f, stop = 1f, fraction = 1f - pageOffset.coerceIn(0f, 1f)).also { scale
            ->
            scaleX = scale
            scaleY = scale
          }

          // We animate the alpha, between 50% and 100%
          alpha = lerp(start = 0.5f, stop = 1f, fraction = 1f - pageOffset.coerceIn(0f, 1f))
        }
    ) {
      AsyncImage(
        modifier = Modifier.fillMaxWidth(),
        model =
          ImageRequest.Builder(LocalContext.current)
            .data(photoUrl)
            .apply {
              if (page == 0) {
                placeholderMemoryCacheKey(photoUrlMemoryCacheKey)
                crossfade(AnimationConstants.DefaultDurationMillis)
              }
            }
            .build(),
        contentDescription = name,
        contentScale = ContentScale.Crop,
      )
    }
  }
}

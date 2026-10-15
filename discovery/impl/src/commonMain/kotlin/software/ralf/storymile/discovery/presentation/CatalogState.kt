package software.ralf.storymile.discovery.presentation

import software.ralf.storymile.discovery.DiscoveredPodcast

sealed interface CatalogState {
  data object Loading : CatalogState

  data object Error : CatalogState

  data class Ready(val podcasts: List<DiscoveredPodcast>) : CatalogState
}

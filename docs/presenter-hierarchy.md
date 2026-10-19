# Presenter hierarchy

Storymile uses the same presenter tree on Android, Desktop, iOS, and Wasm.
Phone and tablet layouts share this tree. Renderers place the template slots for the available window.

## Presenter tree

```mermaid
flowchart TD
  TemplateProvider["TemplateProvider\nlaunchComposePresenter"]
  AppTemplatePresenter["AppTemplatePresenter\ntemplate wrapper"]
  AppRootPresenter["AppRootPresenter"]
  UserPresenter["UserPresenterImpl\nAdaptiveTemplate"]
  HomePresenter["HomePresenterImpl\nbackstack"]
  HomeContentPresenter["HomeContentPresenter\nempty home"]
  DiscoveryPresenterImpl["DiscoveryPresenterImpl\nbrowse, topics, and search"]
  ShowPresenter["ShowPresenter\npodcast preview"]
  EpisodeDetailsPresenter["EpisodeDetailsPresenterImpl\nepisode details"]
  EpisodesPresenter["EpisodesPresenter\nfeed episodes"]
  DownloadsPresenter["DownloadsPresenterImpl\nempty downloads"]
  TabsPresenter["TabsPresenterImpl\ntabs"]
  LibraryPresenter["LibraryPresenterImpl\nempty library"]
  PlaybackPresenter["PlaybackPresenterImpl\ncollapsed and expanded playback"]

  TemplateProvider -->|launches| AppTemplatePresenter
  AppTemplatePresenter --> AppRootPresenter
  AppRootPresenter --> UserPresenter
  UserPresenter --> TabsPresenter
  TabsPresenter -->|Home| HomePresenter
  HomePresenter -->|root| HomeContentPresenter
  HomePresenter -->|pushed entries| DiscoveryPresenterImpl
  HomePresenter -->|pushed entry| ShowPresenter
  HomePresenter -->|pushed entry| EpisodeDetailsPresenter
  DiscoveryPresenterImpl -->|browse and topics| EpisodesPresenter
  ShowPresenter --> EpisodesPresenter
  TabsPresenter -->|Library| LibraryPresenter
  TabsPresenter -->|Downloads| DownloadsPresenter
  UserPresenter --> PlaybackPresenter
```

## Composition

[TemplateProvider](../app-framework/impl/src/commonMain/kotlin/software/ralf/storymile/TemplateProvider.kt)
starts the presenter tree.
[AppTemplatePresenter](../templates/public/src/commonMain/kotlin/software/ralf/storymile/templates/AppTemplatePresenter.kt)
wraps the app root with shared template support.

[AppRootPresenter](../app-framework/impl/src/commonMain/kotlin/software/ralf/storymile/approot/AppRootPresenter.kt)
delegates to the app-scoped
[UserPresenter](../user/public/src/commonMain/kotlin/software/ralf/storymile/user/UserPresenter.kt).
[UserPresenterImpl](../user/impl/src/commonMain/kotlin/software/ralf/storymile/user/UserPresenterImpl.kt)
obtains the
[tabs](../tabs/impl/src/commonMain/kotlin/software/ralf/storymile/tabs/TabsPresenterImpl.kt) and
[playback](../playback/impl/src/commonMain/kotlin/software/ralf/storymile/playback/PlaybackPresenterImpl.kt)
presenters from the active user scope. A scope change resets their state.
The tabs presenter owns the selected destination and produces its content model.
It starts with
[HomePresenterImpl](../home/impl/src/commonMain/kotlin/software/ralf/storymile/home/HomePresenterImpl.kt).
Home owns an App Platform presenter backstack rooted at
[HomeContentPresenter](../home/impl/src/commonMain/kotlin/software/ralf/storymile/home/HomeContentPresenter.kt).
[HomeContentRenderer](../home/impl/src/commonMain/kotlin/software/ralf/storymile/home/HomeContentRenderer.kt)
shows the welcome screen.
Discover and Search push
[DiscoveryPresenterImpl](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/DiscoveryPresenterImpl.kt).
Topic selection pushes another catalog. Podcast selection pushes
[ShowPresenter](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/ShowPresenter.kt).
Both presenters compose
[EpisodesPresenter](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/EpisodesPresenter.kt)
for feed episodes, rendered by the shared
[episode components](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/Episodes.kt).
Episode selection pushes the reusable
[episode details presenter](../episode-details/impl/src/commonMain/kotlin/software/ralf/storymile/episodedetails/EpisodeDetailsPresenterImpl.kt)
through its [public factory](../episode-details/public/src/commonMain/kotlin/software/ralf/storymile/episodedetails/EpisodeDetailsPresenter.kt).
Its [renderer](../episode-details/impl/src/commonMain/kotlin/software/ralf/storymile/episodedetails/EpisodeDetailsRenderer.kt)
shares artwork and episode text with the source row.
The [backstack renderer](../presenter-navigation/impl/src/commonMain/kotlin/software/ralf/storymile/presenternavigation/DefaultBackstackRenderer.kt)
supplies navigation entry identities for these UI transitions.
Back returns to the previous entry. Reselecting Home calls its model callback to pop to the welcome
entry and keeps it in composition.
[HomeRenderer](../home/impl/src/commonMain/kotlin/software/ralf/storymile/home/HomeRenderer.kt)
renders the `DefaultBackstackModel`.
Returning from another tab starts a new Home stack.
The tabs presenter creates and composes only the selected screen. It retains the
[library](../library/impl/src/commonMain/kotlin/software/ralf/storymile/library/LibraryPresenterImpl.kt)
filter across tab changes.
[DownloadsPresenterImpl](../downloads/impl/src/commonMain/kotlin/software/ralf/storymile/downloads/DownloadsPresenterImpl.kt)
provides the empty Downloads screen, rendered by
[DownloadsRenderer](../downloads/impl/src/commonMain/kotlin/software/ralf/storymile/downloads/DownloadsRenderer.kt).
The user presenter places these models in the content, navigation, and playback slots of
[AppTemplate](../templates/public/src/commonMain/kotlin/software/ralf/storymile/templates/AppTemplate.kt).
The playback presenter supplies its slots only when the current user's
[playlists](../playlist/public/src/commonMain/kotlin/software/ralf/storymile/playlist/PlaylistRepository.kt)
contain an episode.

## Adaptive rendering

[AppTemplateRenderer](../templates/impl/src/commonMain/kotlin/software/ralf/storymile/templates/AppTemplateRenderer.kt)
places the template slots for the available window. It adapts navigation placement and playback
presentation while keeping the shared presenter tree. Use the linked source files for model details
and layout rules.

[TabsRenderer](../tabs/impl/src/commonMain/kotlin/software/ralf/storymile/tabs/TabsRenderer.kt)
shows Home, Library, and Downloads with Material icons in every navigation layout.
Side navigation also shows the Storymile logo and title.
Window resizing changes the navigation layout without changing the selected destination.

Home stacks its illustration and discovery prompt in narrow panes and places them side by side in
wide panes. The illustration shrinks to keep the content within the window.

[DiscoveryRenderer](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/DiscoveryRenderer.kt)
adapts browsing, topics, and search to the available width.
[ShowRenderer](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/ShowRenderer.kt)
shows the catalog beside the selected podcast in wide panes. Narrow previews show only the selected
podcast. Both layouts use the same presenter stack.

[LibraryRenderer](../library/impl/src/commonMain/kotlin/software/ralf/storymile/library/LibraryRenderer.kt)
shows Queue, Shows, and Saved placeholders at phone, tablet, and desktop widths.

[PlaybackScreenRenderer](../playback/impl/src/commonMain/kotlin/software/ralf/storymile/playback/PlaybackScreenRenderer.kt)
shows artwork, episode details, playback controls, and chapters. Its collapse button calls the template's
[collapse action](../templates/public/src/commonMain/kotlin/software/ralf/storymile/templates/LocalCollapsePlayback.kt).
[PlaybackBarRenderer](../playback/impl/src/commonMain/kotlin/software/ralf/storymile/playback/PlaybackBarRenderer.kt)
shows artwork and compact controls. In fake mode, both layouts read audio state from the user-scoped
[player](../player/public/src/commonMain/kotlin/software/ralf/storymile/player/Player.kt).
Other playback options retain their placeholder state.
[PlaybackTransition](../templates/public/src/commonMain/kotlin/software/ralf/storymile/templates/PlaybackTransition.kt)
moves shared artwork, text, and controls with the sheet. Controls present in only one layout fade.
The sheet slides up when playback becomes available and down when no playlist contains episodes.

## Source set hierarchy

Shared modules use this [source set hierarchy](../buildSrc/src/main/kotlin/software/ralf/storymile/gradle/KmpPlugin.kt)
with Wasm enabled. The diagram shows production and test sources from `:http:impl`.

[![Shared production and test source sets](images/source-set-hierarchy.svg)](images/source-set-hierarchy.svg)

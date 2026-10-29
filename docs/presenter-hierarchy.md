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
  ShowPresenter["ShowPresenter\nshow backstack"]
  ShowContentPresenter["ShowContentPresenter\npodcast preview"]
  EpisodeDetailsPresenter["EpisodeDetailsPresenterImpl\nepisode details"]
  EpisodesPresenter["EpisodesPresenter\nfeed episodes"]
  DownloadsPresenter["DownloadsPresenterImpl\nempty downloads"]
  TabsPresenter["TabsPresenterImpl\ntabs"]
  LibraryPresenter["LibraryPresenterImpl\nbackstack"]
  LibraryContent["Library content\ntabs"]
  LibraryShowsPresenter["LibraryShowsPresenter\nfollowed shows and saved episodes"]
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
  ShowPresenter -->|root| ShowContentPresenter
  ShowPresenter -->|pushed entry| EpisodeDetailsPresenter
  ShowContentPresenter --> EpisodesPresenter
  TabsPresenter -->|Library| LibraryPresenter
  LibraryPresenter -->|root| LibraryContent
  LibraryPresenter -->|content| LibraryShowsPresenter
  LibraryShowsPresenter -->|opens| EpisodeDetailsPresenter
  LibraryPresenter -->|pushed entries| DiscoveryPresenterImpl
  LibraryPresenter -->|pushed entry| ShowPresenter
  LibraryPresenter -->|pushed entry| EpisodeDetailsPresenter
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
It owns a nested backstack rooted at
[ShowContentPresenter](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/ShowContentPresenter.kt).
Discovery and show content compose
[EpisodesPresenter](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/EpisodesPresenter.kt)
for feed episodes, rendered by the shared
[episode components](../shows/public/src/commonMain/kotlin/software/ralf/storymile/shows/Episodes.kt).
Episode selection pushes the reusable
[episode details presenter](../episode-details/impl/src/commonMain/kotlin/software/ralf/storymile/episodedetails/EpisodeDetailsPresenterImpl.kt)
through its [public factory](../episode-details/public/src/commonMain/kotlin/software/ralf/storymile/episodedetails/EpisodeDetailsPresenter.kt).
Its [renderer](../episode-details/impl/src/commonMain/kotlin/software/ralf/storymile/episodedetails/EpisodeDetailsRenderer.kt)
shares artwork and episode text with the source row in narrow layouts.
The [backstack renderer](../presenter-navigation/impl/src/commonMain/kotlin/software/ralf/storymile/presenternavigation/DefaultBackstackRenderer.kt)
supplies navigation entry identities for these UI transitions.
Back returns to the previous entry. Reselecting Home calls its model callback to pop to the welcome
entry and keeps it in composition.
[HomeRenderer](../home/impl/src/commonMain/kotlin/software/ralf/storymile/home/HomeRenderer.kt)
renders the `DefaultBackstackModel`.
Returning from another tab starts a new Home stack.
The tabs presenter creates and composes only the selected screen. It retains the
[library](../library/impl/src/commonMain/kotlin/software/ralf/storymile/library/LibraryPresenterImpl.kt)
filter across tab changes. Library owns a backstack. Each empty tab opens Discovery.
Back returns to the selected Library tab.
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

[KmpApplication](../app-framework/impl/src/commonMain/kotlin/software/ralf/storymile/KmpApplication.kt)
shows the [boat](../launch-animation/public/src/commonMain/kotlin/software/ralf/storymile/launchanimation/LaunchAnimation.kt)
before it creates the app scope in the background. The host composes the template behind the boat.
The reveal waits for the first content layout and draw. Host recreation resumes an interrupted reveal;
resume does not replay a completed reveal.

[TabsRenderer](../tabs/impl/src/commonMain/kotlin/software/ralf/storymile/tabs/TabsRenderer.kt)
shows Home, Library, and Downloads with Material icons in every navigation layout.
Side navigation also shows the Storymile logo and title.
Window resizing changes the navigation layout without changing the selected destination.

Home stacks its illustration and discovery prompt in narrow panes and places them side by side in
wide panes. The illustration shrinks to keep the content within the window.

[DiscoveryRenderer](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/DiscoveryRenderer.kt)
adapts browsing, topics, and search to the available width.
[ShowRenderer](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/ShowRenderer.kt)
renders the show backstack. In wide layouts, its navigation scene shows the catalog beside
the podcast. Episode selection slides the catalog out, moves the podcast left, and opens episode
details on the right. Back reverses the transition.
[ShowContentRenderer](../discovery/impl/src/commonMain/kotlin/software/ralf/storymile/discovery/presentation/ShowContentRenderer.kt)
renders the podcast and its episodes. Narrow layouts show one destination at a time.
Resizing preserves the selected episode and presenter stack.

[LibraryRenderer](../library/impl/src/commonMain/kotlin/software/ralf/storymile/library/LibraryRenderer.kt)
shows Queue, Shows, Saved, and History tabs.
[LibraryShowsPresenter](../library/impl/src/commonMain/kotlin/software/ralf/storymile/library/LibraryShowsPresenter.kt)
observes saved shows and episodes from the user database. Phones show a list or the selected show.
Wide windows show both panes. Selection survives resizing and tab changes.
Discovery and Library reuse the [show components](../shows/public/src/commonMain/kotlin/software/ralf/storymile/shows/ShowDetails.kt).
The other tabs keep their empty states.

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

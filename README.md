timeline-chart-view
===================

### Description

An android view to represent data over a timeline.

### Demo

[![timeline-chart-view demo video.](http://img.youtube.com/vi/8MhZdYe4I60/0.jpg)](https://www.youtube.com/watch?v=8MhZdYe4I60 "timeline-chart-view demo video.")


### Features

* Display a graph with timeline events.
* Use an android cursor to access the data and register an DataSetObserver to detect changes and automatically refresh the view.
* Scrolling sound effects.
* Auto tick label format selection.
* Auto color scheme generation based on the background color, but also support custom user palettes.
* Various types of graphical representation (normal bars, stacked bars and side-by-side bars)


### Integration

The library is available at [jcenter](https://jcenter.bintray.com/com/ruesga/timeline-chart-view/timeline-chart-view/) and [maven central](https://repo1.maven.org/maven2/com/ruesga/timeline-chart-view/timeline-chart-view/). You can reference it inside gradle script with the next snippets of code.

**jcenter**
```
repositories {
    jcenter()
}

dependencies {
    compile 'com.ruesga.timeline-chart-view:timeline-chart-view:0.0.6'
}
```

**maven central**
```
repositories {
    mavenCentral()
}

dependencies {
    compile 'com.ruesga.timeline-chart-view:timeline-chart-view:0.0.6'
}
```


### Sample code

You can use it by adding a *com.ruesga.timelinechart.TimelineChartView* tag to an android xml layout. You can customize the view behaviour by defining a namespace (xe: xmlns:tlc="http://schemas.android.com/apk/res-auto") and edit its custom properties (see custom behaviour section for more details).
```xml
<com.ruesga.timelinechart.TimelineChartView
    xmlns:tlc="http://schemas.android.com/apk/res-auto"
    android:id="@+id/graph"
    android:layout_width="match_parent"
    android:layout_height="320dp"
    android:background="?attr/colorPrimary"
    tlc:tlcGraphBackground="?attr/colorPrimary"
    tlc:tlcFooterBackground="@color/primary_light"
    tlc:tlcShowFooter="true"
    tlc:tlcGraphMode="tlcBarsStack"/>
```

After that, just obtain the reference as any other android view and pass a cursor to let the library to start observing it. The cursor must contains the next fields (in this order): a long with the timestamp and one or more doubles values (one for every serie to represent int the graph).
```java
Cursor cursor = ...
TimelineChartView mGraph = (TimelineChartView) findViewById(R.id.graph);
mGraph.observeData(cursor);
```

Check out the *sample* directory for a basic usage of the library.

**That's all.** 



### Custom Behaviour

You can configure the some of the behaviour at xml style or at runtime. In addition of the javadoc description in the source code, following is an extended list of the main available behaviours (check the source code and the sample app to obtain a more detail explanation of every property).

**tlcGraphBackground**: Background color of the graph area. This value also determines the autogereated palette of colors, if no user palette was used. **#setGraphAreaBackground(int)** can be used at runtime to set this color.

**tlcFooterBackground**: Background color of the footer area. **#setFooterAreaBackground(int)** can be used at runtime to set this color.

**tlcShowFooter**: A boolean value indicating whether to show/hide the footer of the graph. **#setShowFooter(boolean)** can be used at runtime to show/hide the footer area.

**tlcFooterBarHeight**: A float value indicating the height of footer area of the graph. **#setFooterHeight(float)** can be used at runtime to set the height the footer area.

**tlcBarItemWidth**: A float value indicating the width of a bar item of the graph. **#setBarItemWidth(float)** can be used at runtime to set the width of a bar item.

**tlcBarItemSpace**: A float value indicating the space between bar items. **#setBarItemSpace(float)** can be used at runtime to set the space between bar items.

**tlcGraphMode**: The graph representation mode. This attribute accepts 3 modes: tlcBars (series are draw one over the other), tlcBarsStack (series are draw one on top the other), tlcBarsSideBySide (series are draw one beside the other). **#setGraphMode(int)** can be used at runtime to set the graph mode (see **#GRAPH_MODE_BARS**, **#GRAPH_MODE_BARS_STACK** and **#GRAPH_MODE_BARS_SIDE_BY_SIDE**).

**tlcPlaySelectionSoundEffect**: A boolean value indicating whether play sound effects on item selection. **#setPlaySelectionSoundEffect(boolean)** can be used at runtime to set if the view should reproduce sound effects.

**tlcSelectionSoundEffectSource**: A reference to a raw resource identifier that will be play as sound effect on item selection. Define an invalid resource identifier (value 0) to use the system default sound effect. **#setSelectionSoundEffectSource(int)** can be used at runtime to set the resource to use as sound effect.

**tlcAnimateCursorTransition**: Whether full cursor swap are graphical animated. **#setAnimateCursorTransition(boolean)** can be used at runtime to set.

**tlcFollowCursorPosition**: Whether follow real time cursor updates (live update). Only if scroll is in the last item. **#setFollowCursorPosition(boolean)** can be used at runtime to set the behaviour on cursor updates.

**tlcAlwaysEnsureSelection**: Whether move current view to the nearest selection if, after a user scroll/fling operation, the view is not centered in an item. If **true** move view to the nearest item and selected it. **#setAlwaysEnsureSelection(boolean)** can be used at runtime to set the behaviour when selection requires to be ensured.



### Want to contribute?

Just file new issues and features or send pull requests.


### License

```
Copyright (C) 2015 Jorge Ruesga

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

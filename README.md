SplitImageView
==============

![Preview of SplitImageView](https://raw.githubusercontent.com/aliHafizji/SplitImageView/master/split_image_view.gif) 

## Introduction

Ever wondered how the split image view is implemented in apps like kaliedoscope? This is the android implementation of the split image view.

This view can be used to show a comparison between images or you could use it for its touch to unveil feature.

## Setup

### Method 1: Referencing library project

CreditCardEditText is created as a standalone Android-Library project. You can easily include this project by referencing the library/ folder in your project using either eclipse or Android studio

### Method 2: Adding a remote referece

The compiled version of the library is present on maven central. Using this method is easier than downloading the source and referencing it in your project. To add the library via a remote reference please follow these steps:

* Open the build.gradle file for the module that will use this control.
* At the root level add the following:

```
repositories {
    mavenCentral()
}
```

* In the dependancy section add the following reference:

```
dependencies {
    compile 'com.alihafizji.splitimageview:library:+@aar'
}
```
**Note: The dependacy section can have other dependencies**

* Select the "Sync project with Gradle files" button in Android studio or simply run the assembleDebug gradle task via command line.

## Usage

This view can be intialized using both code as well as xml. All the attributed needed for setup are defined in attrs.xml.

To use it via xml, first add the namespace to the top element of the layout.

```
xmlns:app="http://schemas.android.com/apk/res-auto"
```

Add the view to the layout and set the respective height and width value to suit your needs.

```
<com.alihafizji.splitimageview.SplitImageView
    android:id="@+id/masked_image_view"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:backgroundSrc="@drawable/image_a"
    app:foregroundSrc="@drawable/image_b"
    app:splitPercent="75" />
```

The foregroundSrc and backgroundSrc attributes represent the foreground and background images. The splitPercent attribute shows how much of the backgroundSrc will be unveiled. In the above example only 25% of the background image will be shown.

SplitImageView support all the ScaleTypes that are supported by ImageView. You can simply use the public method setScaleType to change this variable. The scaleType is applied to both the foreground and background image.

Automatic animation: This is a nifty little feature present in SplitImageView. You can turn it on by calling the public method `setEnableAutomaticAnimation(true)`. This will put the unveil animation on a loop, the above preview describes this well. The animation duration for this can also be changed using the public method `setAutomaticAnimationDuration(duration)`.

There are lots of other useful things that the view can do. All the public APIs are well documented and should be easy to use.

## Developed by

* Ali Hafizji <ali.hafizji@gmail.com> 

[Follow me on twitter](https://twitter.com/Ali_hafizji).

## Inspired from

This view has been inspired from Krzysztof Zablocki's implementation of this for iOS. Here is a [link](https://github.com/krzysztofzablocki/KZImageSplitView) to his repository.

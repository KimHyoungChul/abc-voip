BUILD_DIRECTORY := build
FFMPEG_BUILD_DIRECTORY := $(BUILD_DIRECTORY)/ffmpeg
X264_BUILD_DIRECTORY := $(BUILD_DIRECTORY)/x264
FFMPEG_TEST_LIB := lib/libavcodec.a
X264_TEST_LIB := lib/libx264.a

TARGETS := armeabi armeabi-v7a x86

FFMPEG_LIBS := $(foreach TARGET, $(TARGETS),$(FFMPEG_BUILD_DIRECTORY)/$(TARGET)/$(FFMPEG_TEST_LIB))
X264_LIBS := $(foreach TARGET, $(TARGETS),$(X264_BUILD_DIRECTORY)/$(TARGET)/$(X264_TEST_LIB))


all : $(FFMPEG_LIBS) $(X264_LIBS)
	# All lib

$(X264_BUILD_DIRECTORY)/%/$(X264_TEST_LIB) : x264_src/configure
	./build_x264.sh $(subst /$(X264_TEST_LIB),,$(subst $(X264_BUILD_DIRECTORY)/,,$@))


$(FFMPEG_BUILD_DIRECTORY)/%/$(FFMPEG_TEST_LIB) : ffmpeg_src/VERSION $(X264_BUILD_DIRECTORY)/%/$(X264_TEST_LIB)
	./build_ffmpeg.sh $(subst /$(FFMPEG_TEST_LIB),,$(subst $(FFMPEG_BUILD_DIRECTORY)/,,$@))

# Source checkout
ffmpeg_src/VERSION :
	git clone git://git.videolan.org/ffmpeg.git ffmpeg_src; \
	cd ffmpeg_src; \
	git fetch --tags; \
	git checkout origin; \
	git checkout n0.10

x264_src/configure :
	git clone git://git.videolan.org/x264.git x264_src
	# cd x264_src; \
	# git fetch --tags; \
	# git checkout origin; \
	# git checkout 10fe242813531daa61088af158b8b64c6fbe787e


clean :
	rm -r build
	
update :
	# FFmpeg
	cd ffmpeg_src; \
	git fetch --tags; \
	git checkout origin; \
	git checkout n0.10
	# X264
	# cd x264_src; \
	# git checkout origin; \
	# git checkout 10fe242813531daa61088af158b8b64c6fbe787e

"""
Train a first-pass angle classifier from an exported dataset.
Requires: Python 3.10+, tensorflow 2.15+, pillow.
Dataset layout after extraction:
  dataset/images/<task_id>/*.jpg
The Android app exports manifest.csv; review labels before training.
"""
from pathlib import Path
import csv, json
import tensorflow as tf

ROOT = Path("dataset")
IMAGE_ROOT = ROOT / "images"
IMG_SIZE = (224, 224)
BATCH = 16
EPOCHS_HEAD = 12
EPOCHS_FINE = 5

classes = sorted(p.name for p in IMAGE_ROOT.iterdir() if p.is_dir()
                 and any(p.glob("*.jpg")))
if len(classes) < 2:
    raise SystemExit("Need at least two angle classes with images.")
counts = {c: len(list((IMAGE_ROOT/c).glob("*.jpg"))) for c in classes}
too_small = {k:v for k,v in counts.items() if v < 10}
if too_small:
    print("WARNING: classes with fewer than 10 images; results will be unreliable:", too_small)

train = tf.keras.utils.image_dataset_from_directory(
    IMAGE_ROOT, validation_split=0.2, subset="training", seed=42,
    image_size=IMG_SIZE, batch_size=BATCH, label_mode="categorical",
    class_names=classes)
valid = tf.keras.utils.image_dataset_from_directory(
    IMAGE_ROOT, validation_split=0.2, subset="validation", seed=42,
    image_size=IMG_SIZE, batch_size=BATCH, label_mode="categorical",
    class_names=classes)

AUTOTUNE = tf.data.AUTOTUNE
train = train.cache().shuffle(500).prefetch(AUTOTUNE)
valid = valid.cache().prefetch(AUTOTUNE)

base = tf.keras.applications.MobileNetV2(
    input_shape=(*IMG_SIZE, 3), include_top=False, weights="imagenet")
base.trainable = False
inputs = tf.keras.Input(shape=(*IMG_SIZE, 3))
x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)
x = base(x, training=False)
x = tf.keras.layers.GlobalAveragePooling2D()(x)
x = tf.keras.layers.Dropout(0.25)(x)
outputs = tf.keras.layers.Dense(len(classes), activation="softmax")(x)
model = tf.keras.Model(inputs, outputs)
model.compile(optimizer=tf.keras.optimizers.Adam(1e-3),
              loss="categorical_crossentropy", metrics=["accuracy"])
model.fit(train, validation_data=valid, epochs=EPOCHS_HEAD)

base.trainable = True
for layer in base.layers[:-30]:
    layer.trainable = False
model.compile(optimizer=tf.keras.optimizers.Adam(1e-5),
              loss="categorical_crossentropy", metrics=["accuracy"])
model.fit(train, validation_data=valid, epochs=EPOCHS_FINE)

Path("output").mkdir(exist_ok=True)
model.save("output/angle_classifier.keras")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
converter.optimizations = [tf.lite.Optimize.DEFAULT]
tflite = converter.convert()
Path("output/angle_classifier.tflite").write_bytes(tflite)
Path("output/labels.json").write_text(json.dumps(classes, ensure_ascii=False, indent=2))
print("Copy output/angle_classifier.tflite and output/labels.json into app/src/main/assets/ to activate on-device inference.")
print("Saved TFLite model. Validate on photos from trucks not used for training before deployment.")

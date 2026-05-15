import { useState } from 'react';
import {
  Pressable,
  SafeAreaView,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
} from 'react-native';
import { CappedScrollView } from 'react-native-capped-scrollview';

const ROW_COUNT = 20000;
const CAP_OPTIONS = [null, 0, 0.001, 0.01, 0.1, 0.25, 1] as const;

type ScrollLoggerProps = {
  label: string;
  onOffset: (offset: number) => void;
};

const useScrollLogger = ({ label, onOffset }: ScrollLoggerProps) => ({
  onScroll: (e: NativeSyntheticEvent<NativeScrollEvent>) => {
    onOffset(Math.round(e.nativeEvent.contentOffset.y));
  },
  scrollEventThrottle: 16,
  testID: label,
});

const Rows = () =>
  Array.from({ length: ROW_COUNT }, (_, i) => (
    <View key={i} style={styles.row}>
      <Text style={styles.rowText}>Row {i + 1}</Text>
    </View>
  ));

export default function App() {
  const [capIndex, setCapIndex] = useState(4);
  const cap = CAP_OPTIONS[capIndex] ?? null;
  const [cappedOffset, setCappedOffset] = useState(0);
  const [plainOffset, setPlainOffset] = useState(0);

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="dark-content" />
      <View style={styles.header}>
        <Text style={styles.title}>CappedScrollView demo</Text>
        <Text style={styles.subtitle}>
          Fling both lists hard. The capped column resists fast flings.
        </Text>
        <View style={styles.capRow}>
          <Text style={styles.capLabel}>maxVelocity:</Text>
          {CAP_OPTIONS.map((value, i) => (
            <Pressable
              key={String(value)}
              testID={`cap-${value}`}
              onPress={() => setCapIndex(i)}
              style={[styles.capChip, capIndex === i && styles.capChipActive]}
            >
              <Text
                style={[
                  styles.capChipText,
                  capIndex === i && styles.capChipTextActive,
                ]}
              >
                {value === null ? 'null' : value}
              </Text>
            </Pressable>
          ))}
        </View>
      </View>

      <View style={styles.columns}>
        <View style={styles.column}>
          <Text style={styles.columnTitle}>
            Capped ({cap === null ? 'null' : cap})
          </Text>
          <Text style={styles.offset} testID="capped-offset">
            offset {cappedOffset}
          </Text>
          <CappedScrollView
            style={styles.scroll}
            maxVelocity={cap}
            {...useScrollLogger({
              label: 'capped-scroll',
              onOffset: setCappedOffset,
            })}
          >
            <Rows />
          </CappedScrollView>
        </View>
        <View style={styles.column}>
          <Text style={styles.columnTitle}>Plain ScrollView</Text>
          <Text style={styles.offset} testID="plain-offset">
            offset {plainOffset}
          </Text>
          <ScrollView
            style={styles.scroll}
            {...useScrollLogger({
              label: 'plain-scroll',
              onOffset: setPlainOffset,
            })}
          >
            <Rows />
          </ScrollView>
        </View>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff' },
  header: { paddingHorizontal: 16, paddingTop: 8, paddingBottom: 12 },
  title: { fontSize: 18, fontWeight: '600' },
  subtitle: { fontSize: 12, color: '#555', marginTop: 4 },
  capRow: { flexDirection: 'row', alignItems: 'center', marginTop: 12 },
  capLabel: { fontSize: 12, color: '#333', marginRight: 8 },
  capChip: {
    paddingHorizontal: 10,
    paddingVertical: 4,
    borderRadius: 12,
    backgroundColor: '#eee',
    marginRight: 6,
  },
  capChipActive: { backgroundColor: '#1f6feb' },
  capChipText: { fontSize: 12, color: '#333' },
  capChipTextActive: { color: '#fff', fontWeight: '600' },
  columns: { flex: 1, flexDirection: 'row' },
  column: {
    flex: 1,
    borderRightWidth: StyleSheet.hairlineWidth,
    borderRightColor: '#ddd',
  },
  columnTitle: {
    fontSize: 13,
    fontWeight: '600',
    paddingHorizontal: 12,
    paddingTop: 8,
  },
  offset: {
    fontSize: 11,
    color: '#666',
    paddingHorizontal: 12,
    paddingBottom: 4,
  },
  scroll: { flex: 1 },
  row: {
    paddingVertical: 16,
    paddingHorizontal: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e5e5e5',
  },
  rowText: { fontSize: 14 },
});

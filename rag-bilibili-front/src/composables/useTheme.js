import { ref, watch } from "vue";

const THEME_KEY = "rag-bilibili-theme";
const theme = ref(localStorage.getItem(THEME_KEY) || "dark");

export function useTheme() {
  const toggleTheme = () => {
    theme.value = theme.value === "dark" ? "light" : "dark";
  };

  const setTheme = (newTheme) => {
    theme.value = newTheme;
  };

  watch(
    theme,
    (newTheme) => {
      document.documentElement.setAttribute("data-theme", newTheme);
      localStorage.setItem(THEME_KEY, newTheme);
    },
    { immediate: true }
  );

  return {
    theme,
    toggleTheme,
    setTheme,
    isDark: () => theme.value === "dark",
  };
}

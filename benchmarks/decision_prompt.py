"""The decision prompt, shared by training data and the benchmark so both use
exactly the same wording."""

SYSTEM = ("You control a character in a survival game. Choose the best action for "
          "the situation. Reply with exactly one word: {options}.")


def system_text(options):
    return SYSTEM.format(options=", ".join(options[:-1]) + ", or " + options[-1])


def user_text(situation):
    return f"{situation} Which action?"
